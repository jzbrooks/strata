package com.jzbrooks.strata

internal object ArchitectureRendering {
  fun violations(model: ArchitectureModel, edges: List<DependencyEdge>): List<String> =
      edges.mapNotNull { edge ->
        if (edge.configuration in model.ignoredConfigurationNames) return@mapNotNull null
        if (ArchitectureModelBuilder.isIgnored(edge.sourcePath, model.ignoredProjectPaths))
            return@mapNotNull null
        if (ArchitectureModelBuilder.isIgnored(edge.targetPath, model.ignoredProjectPaths))
            return@mapNotNull null
        if (AllowedEdge(edge.sourcePath, edge.targetPath) in model.allowances)
            return@mapNotNull null
        val source = model.classificationsByPath[edge.sourcePath] ?: return@mapNotNull null
        val target = model.classificationsByPath[edge.targetPath] ?: return@mapNotNull null
        if (
            source.layer.projectPath == target.layer.projectPath ||
                target.layer.projectPath in source.layer.effectiveDependencies
        )
            return@mapNotNull null
        violation(model, edge, source, target)
      }

  fun report(model: ArchitectureModel): String =
      buildString {
            appendLine("Architectural layers")
            model.layers.forEachIndexed { index, layer ->
              appendLine()
              appendLine("${index + 1}. Layer project: ${layer.projectPath}")
              appendLine()
              appendLine("   Projects:")
              model.classificationsByPath.values
                  .filter { it.layer.index == layer.index }
                  .map { it.projectPath }
                  .sorted()
                  .forEach { appendLine("     $it") }
              appendLine()
              appendLine("   Direct dependencies:")
              appendLayers(model, layer.directDependencies)
              appendLine()
              appendLine("   May depend on:")
              appendLine("     ${layer.projectPath}")
              appendLayers(model, layer.effectiveDependencies)
            }
          }
          .trimEnd()

  private fun violation(
      model: ArchitectureModel,
      edge: DependencyEdge,
      source: ProjectClassification,
      target: ProjectClassification,
  ): String {
    val directLayers = model.layers.filter { it.projectPath in source.layer.directDependencies }
    val allowedLayers =
        model.layers.filter {
          it.projectPath == source.layer.projectPath ||
              it.projectPath in source.layer.effectiveDependencies
        }
    return buildString {
      appendLine(
          "Forbidden architectural dependency: ${source.layer.projectPath} -> ${target.layer.projectPath}"
      )
      appendLine()
      appendLine("Source project:          ${source.projectPath}")
      appendLine("Source layer project:    ${source.layer.projectPath}")
      appendLine("Configuration:           ${edge.configuration}")
      appendLine()
      appendLine("Target project:          ${target.projectPath}")
      appendLine("Target layer project:    ${target.layer.projectPath}")
      appendLine()
      appendLine("Declared from:")
      appendLine("  ${edge.buildFile}")
      appendLine()
      appendLine("Declared layer dependencies:")
      if (directLayers.isEmpty()) appendLine("  (none)")
      for (it in directLayers) {
        appendLine("  ${it.projectPath}")
      }
      appendLine()
      appendLine("Allowed layer projects:")
      for (it in allowedLayers) {
        appendLine("  ${it.projectPath}")
      }
      appendLine()
      appendLine("Likely declaration:")
      appendLine("  ${edge.configuration}(project(\"${target.projectPath}\"))")
      appendLine()
      appendLine("Suggested fixes:")
      appendLine(
          "- If architecturally appropriate, add dependsOn(\"${target.layer.projectPath}\") to layer(\"${source.layer.projectPath}\")."
      )
      appendLine(
          "- Move the shared abstraction into the '${source.layer.projectPath}' project subtree."
      )
      appendLine(
          "- Reverse or invert the dependency so '${target.layer.projectPath}' does not own a dependency required by '${source.layer.projectPath}'."
      )
      appendLine("- Reconsider the layer boundary represented by '${target.layer.projectPath}'.")
      append(
          "- Add a narrow documented exception only when the violation is intentional and temporary."
      )
    }
  }

  private fun StringBuilder.appendLayers(model: ArchitectureModel, paths: Set<String>) {
    val layers = model.layers.filter { it.projectPath in paths }
    if (layers.isEmpty()) appendLine("     (none)")
    for (it in layers) {
      appendLine("     ${it.projectPath}")
    }
  }
}
