package com.jzbrooks.strata

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
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

    project.gradle.projectsEvaluated {
      finalizeExtension(extension)

      val includedProjects =
          project.allprojects
              .filter { it != project }
              .map { candidate -> ProjectIdentity(candidate.path, relativeBuildFile(candidate)) }
      val model = ArchitectureModelBuilder.build(extension, includedProjects)
      val edges = collectEdges(project)

      checkTask.configure { it.violations.set(ArchitectureRendering.violations(model, edges)) }
      reportTask.configure { it.reportText.set(ArchitectureRendering.report(model)) }
    }
  }

  private fun relativeBuildFile(project: Project): String =
      runCatching {
            project.rootDir
                .toPath()
                .relativize(project.buildFile.toPath())
                .toString()
                .replace('\\', '/')
          }
          .getOrDefault(project.buildFile.path)

  private fun finalizeExtension(extension: StrataExtension) {
    for (layer in extension.layers()) {
      layer.dependencyPaths.finalizeValue()
    }
    extension.ignoredProjectPaths.finalizeValue()
    extension.ignoredConfigurationNames.finalizeValue()
    extension.unclassifiedProjects.finalizeValue()
  }

  private fun collectEdges(rootProject: Project): List<DependencyEdge> = buildList {
    rootProject.allprojects
        .filter { it != rootProject }
        .forEach { source ->
          val relativeBuildFile = relativeBuildFile(source)
          source.configurations
              .filter { it.isCanBeDeclared }
              .forEach { configuration ->
                configuration.dependencies.withType(ProjectDependency::class.java).forEach {
                    dependency ->
                  add(
                      DependencyEdge(
                          sourcePath = source.path,
                          targetPath = dependency.path,
                          configuration = configuration.name,
                          buildFile = relativeBuildFile,
                      ),
                  )
                }
              }
        }
  }
}
