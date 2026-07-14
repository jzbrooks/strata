package com.jzbrooks.strata

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class CheckArchitecturalLayersTask : DefaultTask() {
  @get:Input abstract val violations: ListProperty<String>

  init {
    group = "verification"
    description = "Checks project dependencies against the configured architectural layer graph."
    violations.convention(emptyList())
  }

  @TaskAction
  fun checkArchitecturalLayers() {
    val failures = violations.get()
    if (failures.isNotEmpty()) {
      throw GradleException(failures.joinToString("\n\n${"=".repeat(80)}\n\n"))
    }
    logger.lifecycle("No forbidden architectural dependencies found.")
  }
}

@CacheableTask
abstract class ArchitectureLayersReportTask : DefaultTask() {
  @get:Input abstract val reportText: Property<String>

  init {
    group = "help"
    description = "Reports projects and dependencies in the architectural layer graph."
    reportText.convention("Architectural layers")
  }

  @TaskAction
  fun report() {
    logger.quiet(reportText.get())
  }
}
