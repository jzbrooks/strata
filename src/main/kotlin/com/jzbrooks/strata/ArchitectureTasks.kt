package com.jzbrooks.strata

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "The task produces no outputs and only verifies its inputs")
public abstract class CheckArchitecturalLayersTask : DefaultTask() {
  @get:Input public abstract val violations: ListProperty<String>

  init {
    group = "verification"
    description = "Checks project dependencies against the configured architectural layer graph."
    violations.convention(emptyList())
  }

  @TaskAction
  public fun checkArchitecturalLayers() {
    val failures = violations.get()
    if (failures.isNotEmpty()) {
      throw GradleException(failures.joinToString("\n\n${"=".repeat(80)}\n\n"))
    }
    logger.lifecycle("No forbidden architectural dependencies found.")
  }
}

@DisableCachingByDefault(because = "The task produces no outputs and only logs to the console")
public abstract class ArchitecturalLayersReportTask : DefaultTask() {
  @get:Input public abstract val reportText: Property<String>

  init {
    group = "help"
    description = "Reports projects and dependencies in the architectural layer graph."
    reportText.convention("Architectural layers")
  }

  @TaskAction
  public fun report() {
    logger.quiet(reportText.get())
  }
}
