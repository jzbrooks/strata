package com.jzbrooks.strata

data class LayerDefinition(
    val name: String,
    val index: Int,
    val projectRoots: Set<String>,
)

data class ProjectClassification(
    val projectPath: String,
    val projectRoot: String,
    val layer: LayerDefinition,
)

internal data class ArchitectureModel(
    val layers: List<LayerDefinition>,
    val layersByProjectRoot: Map<String, LayerDefinition>,
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
)
