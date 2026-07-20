package com.jzbrooks.strata

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "The task produces no outputs and only verifies its inputs")
public abstract class CheckArchitecturalLayersTask : DefaultTask() {
  @get:Input public abstract val violations: ListProperty<String>
  @get:Input internal abstract val layerDefinitions: ListProperty<String>
  @get:Input internal abstract val projectClassifications: ListProperty<String>
  @get:Input internal abstract val ignoredProjectPaths: ListProperty<String>
  @get:Input internal abstract val ignoredConfigurationNames: ListProperty<String>
  @get:Input internal abstract val allowances: ListProperty<String>
  @get:Internal internal abstract val dependencyEdgesService: Property<DependencyEdgesService>

  init {
    group = "verification"
    description = "Checks project dependencies against the configured architectural layer graph."
    violations.convention(emptyList())
    layerDefinitions.convention(emptyList())
    projectClassifications.convention(emptyList())
    ignoredProjectPaths.convention(emptyList())
    ignoredConfigurationNames.convention(emptyList())
    allowances.convention(emptyList())
  }

  @TaskAction
  public fun checkArchitecturalLayers() {
    val computed =
        ArchitectureRendering.violations(
            decodeArchitectureModel(this),
            dependencyEdgesService.get().snapshot(),
        )
    val failures = violations.get() + computed
    if (failures.isNotEmpty()) {
      throw GradleException(failures.joinToString("\n\n${"=".repeat(80)}\n\n"))
    }
    logger.lifecycle("No forbidden architectural dependencies found.")
  }
}

private fun decodeArchitectureModel(task: CheckArchitecturalLayersTask): ArchitectureModel {
  val layers =
      task.layerDefinitions.get().mapIndexed { index, encoded ->
        val fields = encoded.split(FIELD_SEPARATOR)
        LayerDefinition(
            fields[0],
            index,
            fields[1].split(',').filterTo(linkedSetOf()) { it.isNotEmpty() },
            fields[2].split(',').filterTo(linkedSetOf()) { it.isNotEmpty() },
        )
      }
  val layersByPath = layers.associateBy { it.projectPath }
  val classifications =
      task.projectClassifications.get().associate { encoded ->
        val fields = encoded.split(FIELD_SEPARATOR)
        fields[0] to ProjectClassification(fields[0], layersByPath.getValue(fields[1]))
      }
  val allowedEdges =
      task.allowances.get().mapTo(mutableSetOf()) { encoded ->
        val fields = encoded.split(FIELD_SEPARATOR)
        AllowedEdge(fields[0], fields[1])
      }
  return ArchitectureModel(
      layers,
      classifications,
      task.ignoredProjectPaths.get().toSet(),
      task.ignoredConfigurationNames.get().toSet(),
      allowedEdges,
  )
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
