package com.jzbrooks.strata

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin

public class StrataPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    if (project.path != project.isolated.rootProject.path) {
      throw GradleException(
          "The com.jzbrooks.strata plugin must only be applied to the root project."
      )
    }

    project.pluginManager.apply("base")
    val extension =
        project.extensions.create(
            "strata",
            StrataExtension::class.java,
        )
    val checkTask =
        project.tasks.register(
            "checkArchitecturalLayers",
            CheckArchitecturalLayersTask::class.java,
        )
    val reportTask =
        project.tasks.register(
            "architecturalLayersReport",
            ArchitecturalLayersReportTask::class.java,
        )
    project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { it.dependsOn(checkTask) }
    val dependencyEdgesService =
        project.gradle.sharedServices.registerIfAbsent(
            DEPENDENCY_EDGES_SERVICE,
            DependencyEdgesService::class.java,
        ) {
          it.parameters.bootstrapApplied.convention(false)
        }

    project.afterEvaluate {
      if (!dependencyEdgesService.get().parameters.bootstrapApplied.get()) {
        throw GradleException(
            """
            Strata dependency collection is not enabled.

            Apply the collector plugin in settings.gradle.kts:

            plugins {
                id("com.jzbrooks.strata.collector") version "<same version>"
            }
            """
                .trimIndent()
        )
      }
      finalizeExtension(extension)

      val includedProjects =
          project.allprojects
              .filter { it != project }
              .map { candidate -> ProjectIdentity(candidate.isolated.path, "") }
      val model = ArchitectureModelBuilder.build(extension, includedProjects)
      checkTask.configure {
        it.dependsOn(
            includedProjects.map { identity -> "${identity.path}:$COLLECT_DEPENDENCIES_TASK" }
        )
        it.dependencyEdgesService.set(dependencyEdgesService)
        it.usesService(dependencyEdgesService)
        it.layerDefinitions.set(model.layers.map(::encodeLayer))
        it.projectClassifications.set(
            model.classificationsByPath.values.map { classification ->
              listOf(classification.projectPath, classification.layer.projectPath)
                  .joinToString(FIELD_SEPARATOR.toString())
            }
        )
        it.ignoredProjectPaths.set(model.ignoredProjectPaths.toList())
        it.ignoredConfigurationNames.set(model.ignoredConfigurationNames.toList())
        it.allowances.set(
            model.allowances.map { allowance ->
              listOf(allowance.from, allowance.to).joinToString(FIELD_SEPARATOR.toString())
            }
        )
      }
      reportTask.configure { it.reportText.set(ArchitectureRendering.report(model)) }
    }
  }

  private fun encodeLayer(layer: LayerDefinition): String =
      listOf(
              layer.projectPath,
              layer.directDependencies.joinToString(","),
              layer.effectiveDependencies.joinToString(","),
          )
          .joinToString(FIELD_SEPARATOR.toString())

  private fun finalizeExtension(extension: StrataExtension) {
    for (layer in extension.layers()) {
      layer.dependencyPaths.finalizeValue()
    }
    extension.ignoredProjectPaths.finalizeValue()
    extension.ignoredConfigurationNames.finalizeValue()
    extension.unclassifiedProjects.finalizeValue()
  }
}
