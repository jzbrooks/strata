package com.jzbrooks.strata

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.language.base.plugins.LifecycleBasePlugin

class ArchitectureLayersPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    if (project != project.rootProject) {
      throw GradleException(
          "The com.jzbrooks.strata plugin must be applied to the root project only."
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
            "checkArchitectureLayers",
            CheckArchitectureLayersTask::class.java,
        )
    val reportTask =
        project.tasks.register(
            "architectureLayersReport",
            ArchitectureLayersReportTask::class.java,
        )
    project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { it.dependsOn(checkTask) }

    project.gradle.projectsEvaluated {
      finalizeExtension(extension)

      val rootDirectory = project.rootDir.toPath()
      val includedProjects =
          project.allprojects
              .filter { it != project }
              .map { candidate ->
                val relativeBuildFile =
                    runCatching {
                          rootDirectory
                              .relativize(candidate.buildFile.toPath())
                              .toString()
                              .replace('\\', '/')
                        }
                        .getOrDefault(candidate.buildFile.path)
                ProjectIdentity(candidate.path, relativeBuildFile)
              }
      val model = ArchitectureModelBuilder.build(extension, includedProjects)
      val edges = collectEdges(project)

      checkTask.configure { it.violations.set(ArchitectureRendering.violations(model, edges)) }
      reportTask.configure { it.reportText.set(ArchitectureRendering.report(model)) }
    }
  }

  private fun finalizeExtension(extension: StrataExtension) {
    extension.layers().forEach { it.projectRoots.finalizeValue() }
    extension.ignoredProjectPaths.finalizeValue()
    extension.ignoredConfigurationNames.finalizeValue()
    extension.unclassifiedProjects.finalizeValue()
  }

  private fun collectEdges(rootProject: Project): List<DependencyEdge> = buildList {
    rootProject.allprojects
        .filter { it != rootProject }
        .forEach { source ->
          val relativeBuildFile =
              runCatching {
                    rootProject.rootDir
                        .toPath()
                        .relativize(source.buildFile.toPath())
                        .toString()
                        .replace('\\', '/')
                  }
                  .getOrDefault(source.buildFile.path)
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
