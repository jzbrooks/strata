package com.jzbrooks.strata

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "The task verifies dependency metadata held by a build service")
public abstract class CheckArchitecturalLayersTask : DefaultTask() {
  @get:Input internal abstract val layerDefinitions: ListProperty<String>
  @get:Input internal abstract val projectClassifications: ListProperty<String>
  @get:Input internal abstract val ignoredProjectPaths: ListProperty<String>
  @get:Input internal abstract val ignoredConfigurationNames: ListProperty<String>
  @get:Input internal abstract val allowances: ListProperty<String>
  @get:Internal internal abstract val dependencyEdgesService: Property<DependencyEdgesService>
  @get:Internal internal abstract val reportFile: RegularFileProperty

  init {
    group = "verification"
    description = "Checks project dependencies against the configured architectural layer graph."
    initializeModelProperties()
  }

  @TaskAction
  public fun checkArchitecturalLayers() {
    val violations =
        ArchitectureAnalysis.analyze(
                decodeArchitectureModel(
                    layerDefinitions.get(),
                    projectClassifications.get(),
                    ignoredProjectPaths.get(),
                    ignoredConfigurationNames.get(),
                    allowances.get(),
                ),
                dependencyEdgesService.get().snapshot(),
            )
            .violations
    val count = violations.size
    val path = reportFile.get().asFile.absolutePath
    if (count > 0) {
      logger.lifecycle(renderFailureList(violations.take(5)))
      throw GradleException(
          "Found $count forbidden architectural ${if (count == 1) "dependency" else "dependencies"}. See report: $path"
      )
    }
  }

  private fun renderFailureList(violations: List<AnalyzedDependency>): String =
      buildString {
            violations.forEachIndexed { index, violation ->
              if (index > 0) appendLine()
              val edge = violation.edge
              val location = "${edge.buildFile}:${edge.lineNumber}"
              appendLine("${index + 1}. $location")
              appendLine("   ${"─".repeat(location.length)}")
              append("   ${edge.declaration}")
            }
          }
          .trimEnd()

  private fun initializeModelProperties() {
    layerDefinitions.convention(emptyList())
    projectClassifications.convention(emptyList())
    ignoredProjectPaths.convention(emptyList())
    ignoredConfigurationNames.convention(emptyList())
    allowances.convention(emptyList())
  }
}

@DisableCachingByDefault(
    because = "Dependency edges are held by an in-memory build service and cannot be tracked safely"
)
public abstract class ArchitecturalLayersReportTask : DefaultTask() {
  @get:OutputFile public abstract val reportFile: RegularFileProperty
  @get:Input internal abstract val layerDefinitions: ListProperty<String>
  @get:Input internal abstract val projectClassifications: ListProperty<String>
  @get:Input internal abstract val ignoredProjectPaths: ListProperty<String>
  @get:Input internal abstract val ignoredConfigurationNames: ListProperty<String>
  @get:Input internal abstract val allowances: ListProperty<String>
  @get:Internal internal abstract val dependencyEdgesService: Property<DependencyEdgesService>

  init {
    group = "help"
    description = "Writes the architectural layer dependency report."
    outputs.upToDateWhen { false }
    layerDefinitions.convention(emptyList())
    projectClassifications.convention(emptyList())
    ignoredProjectPaths.convention(emptyList())
    ignoredConfigurationNames.convention(emptyList())
    allowances.convention(emptyList())
  }

  @TaskAction
  public fun report() {
    val report =
        ArchitectureAnalysis.analyze(
            decodeArchitectureModel(
                layerDefinitions.get(),
                projectClassifications.get(),
                ignoredProjectPaths.get(),
                ignoredConfigurationNames.get(),
                allowances.get(),
            ),
            dependencyEdgesService.get().snapshot(),
        )
    val output = reportFile.get().asFile
    output.parentFile.mkdirs()
    output.writeText(ArchitectureRendering.render(report))
    logger.lifecycle("Architectural layers report: ${output.absolutePath}")
  }
}

private fun decodeArchitectureModel(
    layerDefinitions: List<String>,
    projectClassifications: List<String>,
    ignoredProjectPaths: List<String>,
    ignoredConfigurationNames: List<String>,
    allowances: List<String>,
): ArchitectureModel {
  val layers = layerDefinitions.mapIndexed { index, encoded ->
    val fields = encoded.split(FIELD_SEPARATOR)
    LayerDefinition(
        fields[0],
        index,
        fields[1].split(',').filterTo(linkedSetOf()) { it.isNotEmpty() },
        fields[2].split(',').filterTo(linkedSetOf()) { it.isNotEmpty() },
    )
  }
  val layersByPath = layers.associateBy { it.projectPath }
  val classifications = projectClassifications.associate { encoded ->
    val fields = encoded.split(FIELD_SEPARATOR)
    fields[0] to ProjectClassification(fields[0], layersByPath.getValue(fields[1]))
  }
  val allowedEdges =
      allowances.mapTo(mutableSetOf()) { encoded ->
        val fields = encoded.split(FIELD_SEPARATOR)
        AllowedEdge(fields[0], fields[1])
      }
  return ArchitectureModel(
      layers,
      classifications,
      ignoredProjectPaths.toSet(),
      ignoredConfigurationNames.toSet(),
      allowedEdges,
  )
}
