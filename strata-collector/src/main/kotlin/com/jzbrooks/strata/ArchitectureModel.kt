package com.jzbrooks.strata

internal data class LayerDefinition(
    val projectPath: String,
    val index: Int,
    val directDependencies: Set<String>,
    val effectiveDependencies: Set<String>,
)

internal data class ProjectClassification(
    val projectPath: String,
    val layer: LayerDefinition,
)

internal data class ArchitectureModel(
    val layers: List<LayerDefinition>,
    val classificationsByPath: Map<String, ProjectClassification>,
    val ignoredProjectPaths: Set<String>,
    val ignoredConfigurationNames: Set<String>,
    val allowances: Set<AllowedEdge>,
)

internal data class AllowedEdge(val from: String, val to: String)

internal data class ProjectIdentity(
    val path: String,
    val buildFile: String,
)

internal data class DependencyEdge(
    val sourcePath: String,
    val targetPath: String,
    val configuration: String,
    val buildFile: String,
    val declaration: String = "$configuration(project(\"$targetPath\"))",
)
