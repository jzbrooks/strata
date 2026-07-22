package com.jzbrooks.strata

internal enum class DependencyDisposition(val label: String) {
  FORBIDDEN("forbidden"),
  ALLOWED("allowed"),
  SAME_LAYER("same-layer"),
  EXPLICITLY_ALLOWED("explicitly-allowed"),
  IGNORED("ignored"),
}

internal data class AnalyzedDependency(
    val edge: DependencyEdge,
    val source: ProjectClassification?,
    val target: ProjectClassification?,
    val disposition: DependencyDisposition,
)

internal data class ArchitectureReport(
    val architecture: ArchitectureModel,
    val dependencies: List<AnalyzedDependency>,
) {
  val violations: List<AnalyzedDependency>
    get() = dependencies.filter { it.disposition == DependencyDisposition.FORBIDDEN }
}

internal object ArchitectureAnalysis {
  private val edgeComparator =
      compareBy<DependencyEdge>(
          { it.sourcePath },
          { it.targetPath },
          { it.configuration },
          { it.buildFile },
      )

  fun analyze(model: ArchitectureModel, edges: List<DependencyEdge>): ArchitectureReport =
      ArchitectureReport(
          model,
          edges.sortedWith(edgeComparator).map { edge ->
            val source = model.classificationsByPath[edge.sourcePath]
            val target = model.classificationsByPath[edge.targetPath]
            val disposition =
                when {
                  edge.configuration in model.ignoredConfigurationNames ->
                      DependencyDisposition.IGNORED
                  ArchitectureModelBuilder.isIgnored(edge.sourcePath, model.ignoredProjectPaths) ||
                      ArchitectureModelBuilder.isIgnored(
                          edge.targetPath,
                          model.ignoredProjectPaths,
                      ) -> DependencyDisposition.IGNORED
                  AllowedEdge(edge.sourcePath, edge.targetPath) in model.allowances ->
                      DependencyDisposition.EXPLICITLY_ALLOWED
                  source == null || target == null -> DependencyDisposition.IGNORED
                  source.layer.projectPath == target.layer.projectPath ->
                      DependencyDisposition.SAME_LAYER
                  target.layer.projectPath in source.layer.effectiveDependencies ->
                      DependencyDisposition.ALLOWED
                  else -> DependencyDisposition.FORBIDDEN
                }
            AnalyzedDependency(edge, source, target, disposition)
          },
      )
}

internal object ArchitectureRendering {
  fun render(report: ArchitectureReport): String =
      buildString {
            val violations = report.violations
            appendLine("Strata architectural layers report")
            appendLine("Status: ${if (violations.isEmpty()) "PASSED" else "FAILED"}")
            appendLine("Violations: ${violations.size}")
            appendLine()
            appendLine("Forbidden dependencies")
            if (violations.isEmpty()) appendLine("  (none)")
            violations.forEach { dependency ->
              val edge = dependency.edge
              appendLine(
                  "  ${edge.sourcePath} (${dependency.source?.layer?.projectPath ?: "unclassified"}) -> " +
                      "${edge.targetPath} (${dependency.target?.layer?.projectPath ?: "unclassified"}) " +
                      "[${edge.configuration}; ${edge.buildFile}]"
              )
            }

            appendLine()
            appendLine("Offending dependency trees")
            val roots = violations.map { it.edge.sourcePath }.distinct().sorted()
            if (roots.isEmpty()) appendLine("  (none)")
            val outgoing = violations.groupBy { it.edge.sourcePath }
            roots.forEachIndexed { index, root ->
              if (index > 0) appendLine()
              appendLine("  $root")
              appendTree(root, outgoing, "    ", linkedSetOf(root), mutableSetOf(root))
            }

            appendClassification(report.architecture)
          }
          .trimEnd() + "\n"

  private fun StringBuilder.appendTree(
      source: String,
      outgoing: Map<String, List<AnalyzedDependency>>,
      indent: String,
      ancestors: LinkedHashSet<String>,
      expanded: MutableSet<String>,
  ) {
    for (dependency in outgoing[source].orEmpty()) {
      val target = dependency.edge.targetPath
      append(indent)
      append(
          "-> $target [${dependency.disposition.label}; ${dependency.edge.configuration}; ${dependency.edge.buildFile}]"
      )
      when {
        target in ancestors -> appendLine(" (cycle -> $target)")
        target in expanded -> appendLine(" (shared -> $target)")
        else -> {
          appendLine()
          expanded += target
          ancestors += target
          appendTree(target, outgoing, "$indent  ", ancestors, expanded)
          ancestors -= target
        }
      }
    }
  }

  private fun StringBuilder.appendClassification(model: ArchitectureModel) {
    appendLine()
    appendLine("Layer classification and permissions")
    model.layers.forEachIndexed { index, layer ->
      appendLine()
      appendLine("  ${index + 1}. Layer project: ${layer.projectPath}")
      appendLine("     Projects:")
      val projects =
          model.classificationsByPath.values
              .filter { it.layer.index == layer.index }
              .map { it.projectPath }
              .sorted()
      if (projects.isEmpty()) appendLine("       (none)")
      projects.forEach { appendLine("       $it") }
      appendLine("     Direct dependencies:")
      appendLayers(model, layer.directDependencies)
      appendLine("     May depend on:")
      appendLine("       ${layer.projectPath}")
      appendLayers(model, layer.effectiveDependencies)
    }
  }

  private fun StringBuilder.appendLayers(model: ArchitectureModel, paths: Set<String>) {
    val layers = model.layers.filter { it.projectPath in paths }
    if (layers.isEmpty()) appendLine("       (none)")
    layers.forEach { appendLine("       ${it.projectPath}") }
  }
}
