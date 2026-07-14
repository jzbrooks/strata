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
            source.layer.name == target.layer.name ||
                target.layer.name in source.layer.effectiveDependencies
        )
            return@mapNotNull null
        violation(model, edge, source, target)
      }

  fun report(model: ArchitectureModel): String =
      buildString {
            appendLine("Architectural layers")
            model.layers.forEachIndexed { index, layer ->
              appendLine()
              appendLine("${index + 1}. ${layer.name}")
              appendLine("   Top-level roots:")
              layer.projectRoots.sorted().forEach { appendLine("     :$it") }
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
              appendLine("     ${layer.name}")
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
    val directLayers =
        model.layers.filter { candidate -> candidate.name in source.layer.directDependencies }
    val allowedLayers =
        model.layers.filter { candidate ->
          candidate.name == source.layer.name ||
              candidate.name in source.layer.effectiveDependencies
        }
    val sourceMembers = source.layer.projectRoots.sorted()
    val allowedRoots = allowedLayers.flatMap { it.projectRoots }.sorted()
    return buildString {
      appendLine("Forbidden architectural dependency: ${source.layer.name} -> ${target.layer.name}")
      appendLine()
      appendLine("Source project:          ${source.projectPath}")
      appendLine("Source project root:     :${source.projectRoot}")
      appendLine("Source layer:            ${source.layer.name}")
      appendLine("Configuration:           ${edge.configuration}")
      appendLine()
      appendLine("Target project:          ${target.projectPath}")
      appendLine("Target project root:     :${target.projectRoot}")
      appendLine("Target layer:            ${target.layer.name}")
      appendLine()
      appendLine("Declared from:")
      appendLine("  ${edge.buildFile}")
      appendLine()
      appendLine("Declared layer dependencies:")
      if (directLayers.isEmpty()) appendLine("  (none)")
      directLayers.forEach { appendLine("  ${it.name}") }
      appendLine()
      appendLine("Source layer members:")
      sourceMembers.forEach { appendLine("  :$it") }
      appendLine()
      appendLine("Allowed target layers:")
      allowedLayers.forEach { appendLine("  ${it.name}") }
      appendLine()
      appendLine("Allowed top-level project roots:")
      allowedRoots.forEach { appendLine("  :$it") }
      appendLine()
      appendLine("Likely declaration:")
      appendLine("  ${edge.configuration}(project(\"${target.projectPath}\"))")
      appendLine()
      appendLine("Suggested fixes:")
      appendLine(
          "- If architecturally appropriate, add dependsOn(\"${target.layer.name}\") to the '${source.layer.name}' layer."
      )
      appendLine("- Move the shared abstraction to a project in the '${source.layer.name}' layer.")
      appendLine(
          "- Reverse or invert the dependency so the '${target.layer.name}' layer does not own a dependency required by '${source.layer.name}'."
      )
      appendLine(
          "- Reconsider whether ':${target.projectRoot}' belongs in the '${target.layer.name}' layer."
      )
      append(
          "- Add a narrow documented exception only when the violation is intentional and temporary."
      )
    }
  }

  private fun StringBuilder.appendLayers(model: ArchitectureModel, names: Set<String>) {
    val layers = model.layers.filter { it.name in names }
    if (layers.isEmpty()) appendLine("     (none)")
    layers.forEach { appendLine("     ${it.name}") }
  }
}
