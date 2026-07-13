package com.jzbrooks.strata

import org.gradle.api.GradleException

internal object ArchitectureModelBuilder {
  private val absoluteProjectPath = Regex("^:[^:\\s]+(?::[^:\\s]+)*$")

  fun build(
      extension: StrataExtension,
      projects: List<ProjectIdentity>,
  ): ArchitectureModel {
    val errors = mutableListOf<String>()
    val projectPaths = projects.mapTo(linkedSetOf()) { it.path }
    val seenLayerNames = linkedSetOf<String>()
    val rootAssignments = linkedMapOf<String, MutableList<String>>()

    if (extension.layers().isEmpty()) {
      errors += "At least one architectural layer must be declared."
    }

    val layers =
        extension.layers().mapIndexed { index, spec ->
          if (spec.name.isBlank()) {
            errors += "Architectural layer names must not be blank."
          } else if (!seenLayerNames.add(spec.name)) {
            errors += "Architectural layer '${spec.name}' is declared more than once."
          }

          val rawRoots = spec.projectRoots.orNull.orEmpty().toList()
          if (rawRoots.isEmpty()) {
            errors +=
                "Architectural layer '${spec.name}' must declare at least one top-level project root."
          }

          val normalizedRoots = linkedSetOf<String>()
          rawRoots.forEach { raw ->
            val normalized = normalizeProjectRoot(raw, spec.name, errors) ?: return@forEach
            if (!normalizedRoots.add(normalized)) {
              errors +=
                  "Top-level project root ':$normalized' is declared more than once in architectural layer '${spec.name}'."
            }
            rootAssignments.getOrPut(normalized) { mutableListOf() } += spec.name
            if (":$normalized" !in projectPaths) {
              errors +=
                  "Top-level project root ':$normalized' configured for architectural layer '${spec.name}' does not exist in this build."
            }
          }
          LayerDefinition(spec.name, index, normalizedRoots)
        }

    rootAssignments
        .filterValues { it.size > 1 }
        .forEach { (root, assignedLayers) -> errors += duplicateRootMessage(root, assignedLayers) }

    val ignoredPaths =
        extension.ignoredProjectPaths.orNull.orEmpty().toList().mapNotNullTo(linkedSetOf()) { path
          ->
          validateExistingProjectPath(path, "Ignored project path", projectPaths, errors)
        }
    val ignoredConfigurations =
        extension.ignoredConfigurationNames.orNull.orEmpty().toList().toSet()
    ignoredConfigurations.filter(String::isBlank).forEach {
      errors += "Ignored configuration names must not be blank."
    }

    val allowances =
        extension.allowances().mapNotNullTo(linkedSetOf()) { allowance ->
          if (allowance.because.isBlank()) {
            errors +=
                "Architecture exception from '${allowance.from}' to '${allowance.to}' must include a non-blank justification."
          }
          val from =
              validateExistingProjectPath(
                  allowance.from,
                  "Architecture exception source",
                  projectPaths,
                  errors,
              )
          val to =
              validateExistingProjectPath(
                  allowance.to,
                  "Architecture exception target",
                  projectPaths,
                  errors,
              )
          if (from != null && to != null) AllowedEdge(from, to) else null
        }

    val byRoot = linkedMapOf<String, LayerDefinition>()
    layers.forEach { layer -> layer.projectRoots.forEach { byRoot.putIfAbsent(it, layer) } }
    val classifications = linkedMapOf<String, ProjectClassification>()
    val unclassified = mutableListOf<String>()
    projects.forEach { project ->
      if (isIgnored(project.path, ignoredPaths)) return@forEach
      val root = project.path.removePrefix(":").substringBefore(':')
      val layer = byRoot[root]
      if (layer == null) {
        unclassified += project.path
      } else {
        classifications[project.path] = ProjectClassification(project.path, root, layer)
      }
    }

    if (
        extension.unclassifiedProjects.get() == UnclassifiedProjectPolicy.FAIL &&
            unclassified.isNotEmpty()
    ) {
      errors += buildString {
        appendLine("The following projects are not assigned to an architectural layer:")
        unclassified.sorted().forEach { appendLine("  $it") }
        append(
            "Assign their top-level roots to a layer, ignore their project subtrees, or set unclassifiedProjects to IGNORE."
        )
      }
    }

    if (errors.isNotEmpty()) {
      throw GradleException(errors.joinToString("\n\n"))
    }

    return ArchitectureModel(
        layers = layers,
        layersByProjectRoot = byRoot,
        classificationsByPath = classifications,
        ignoredProjectPaths = ignoredPaths,
        ignoredConfigurationNames = ignoredConfigurations,
        allowances = allowances,
    )
  }

  fun isIgnored(path: String, ignoredPaths: Set<String>): Boolean = ignoredPaths.any {
    path == it || path.startsWith("$it:")
  }

  private fun normalizeProjectRoot(
      raw: String,
      layerName: String,
      errors: MutableList<String>,
  ): String? {
    if (raw.isBlank()) {
      errors += "Architectural layer '$layerName' contains a blank project root."
      return null
    }
    if (raw == ":") {
      errors += "The root project ':' cannot be used as an architectural project root."
      return null
    }
    val normalized = raw.removePrefix(":")
    if (normalized.isBlank() || ':' in normalized || normalized.any(Char::isWhitespace)) {
      errors +=
          "Project root '$raw' in architectural layer '$layerName' is not a top-level project root; use a value such as 'app' or ':app'."
      return null
    }
    return normalized
  }

  private fun validateExistingProjectPath(
      path: String,
      label: String,
      projectPaths: Set<String>,
      errors: MutableList<String>,
  ): String? {
    if (!absoluteProjectPath.matches(path)) {
      errors +=
          "$label '$path' is malformed; use an absolute non-root Gradle project path such as ':app' or ':app:feature'."
      return null
    }
    if (path !in projectPaths) {
      errors += "$label '$path' does not exist in this build."
      return null
    }
    return path
  }

  private fun duplicateRootMessage(root: String, layers: List<String>): String = buildString {
    appendLine("Top-level project root ':$root' is assigned to multiple architectural layers.")
    appendLine()
    appendLine("Assigned layers:")
    layers.forEach { appendLine("  $it") }
    appendLine()
    appendLine("Each top-level project root must belong to exactly one layer.")
    appendLine()
    append("Remove ':$root' from all but one layer declaration.")
  }
}
