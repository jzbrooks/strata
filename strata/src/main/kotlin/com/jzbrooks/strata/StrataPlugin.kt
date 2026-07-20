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
    project.extensions.create(
        "strata",
        StrataExtension::class.java,
    )

    project.tasks.apply {
      val checkTask =
          register(
              "checkArchitecturalLayers",
              CheckArchitecturalLayersTask::class.java,
          )

      register(
          "architecturalLayersReport",
          ArchitecturalLayersReportTask::class.java,
      )

      named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { it.dependsOn(checkTask) }
    }

    if (project.gradle.sharedServices.registrations.findByName("strataDependencyEdges") == null) {
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
  }
}
