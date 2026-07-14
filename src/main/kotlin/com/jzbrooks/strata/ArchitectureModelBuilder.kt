package com.jzbrooks.strata

import kotlin.collections.linkedSetOf
import org.gradle.api.GradleException

internal object ArchitectureModelBuilder {
  private val absoluteProjectPath = Regex("^:[^:\\s]+(?::[^:\\s]+)*$")
  private val topLevelProjectPath = Regex("^:[^:\\s]+$")

  fun build(extension: StrataExtension, projects: List<ProjectIdentity>): ArchitectureModel {
    val errors = mutableListOf<String>()
    val projectPaths = projects.mapTo(linkedSetOf()) { it.path }
    val seenLayerPaths = linkedSetOf<String>()

    if (extension.layers().isEmpty()) errors += "At least one architectural layer must be declared."

    val unresolvedLayers =
        extension.layers().mapIndexed { index, spec ->
          val path = spec.projectPath
          validateLayerPath(path, projectPaths, errors)
          if (!seenLayerPaths.add(path)) {
            errors += "Architectural layer project '$path' is declared more than once."
          }

          val dependencies = linkedSetOf<String>()
          spec.dependencyPaths.orNull.orEmpty().forEach { dependency ->
            if (validateDependencyPath(path, dependency, errors)) dependencies += dependency
          }
          UnresolvedLayer(path, index, dependencies)
        }

    val layerPaths = unresolvedLayers.mapTo(linkedSetOf()) { it.projectPath }
    unresolvedLayers.forEach { layer ->
      layer.dependencies.forEach { dependency ->
        when (dependency) {
          layer.projectPath ->
              errors +=
                  "Architectural layer project '${layer.projectPath}' must not depend on itself."
          !in layerPaths ->
              errors +=
                  "Architectural layer project '${layer.projectPath}' depends on unknown layer project '$dependency'."
        }
      }
    }
    findCycle(unresolvedLayers)?.let { cycle ->
      errors += "Architectural layer dependency cycle detected: ${cycle.joinToString(" -> ")}"
    }

    val dependenciesByPath = unresolvedLayers.associate { it.projectPath to it.dependencies }
    val layers = unresolvedLayers.map { layer ->
      LayerDefinition(
          layer.projectPath,
          layer.index,
          layer.dependencies,
          reachableDependencies(layer.projectPath, dependenciesByPath),
      )
    }

    val ignoredPaths =
        extension.ignoredProjectPaths.orNull.orEmpty().mapNotNullTo(linkedSetOf()) { path ->
          validateExistingProjectPath(path, "Ignored project path", projectPaths, errors)
        }
    val ignoredConfigurations = extension.ignoredConfigurationNames.orNull.orEmpty().toSet()
    val blankIgnoredConfigurations = ignoredConfigurations.count { it.isBlank() }
    if (blankIgnoredConfigurations > 1) {
      errors += "$blankIgnoredConfigurations ignored configuration names must not be blank."
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

    val byPath = layers.associateByTo(linkedMapOf()) { it.projectPath }
    val classifications = linkedMapOf<String, ProjectClassification>()
    val unclassified = mutableListOf<String>()
    projects.forEach { project ->
      if (isIgnored(project.path, ignoredPaths)) return@forEach
      val layerPath = ":" + project.path.removePrefix(":").substringBefore(':')
      val layer = byPath[layerPath]
      if (layer == null) unclassified += project.path
      else classifications[project.path] = ProjectClassification(project.path, layer)
    }

    if (
        extension.unclassifiedProjects.get() == UnclassifiedProjectPolicy.FAIL &&
            unclassified.isNotEmpty()
    ) {
      errors += buildString {
        appendLine("The following projects are not assigned to an architectural layer:")
        unclassified.sorted().forEach { appendLine("  $it") }
        append(
            "Declare their top-level projects as layers, ignore their project subtrees, or set unclassifiedProjects to IGNORE."
        )
      }
    }

    if (errors.isNotEmpty()) throw GradleException(errors.joinToString("\n\n"))
    return ArchitectureModel(
        layers,
        byPath,
        classifications,
        ignoredPaths,
        ignoredConfigurations,
        allowances,
    )
  }

  fun isIgnored(path: String, ignoredPaths: Set<String>): Boolean = ignoredPaths.any {
    path == it || path.startsWith("$it:")
  }

  private fun validateLayerPath(
      path: String,
      projectPaths: Set<String>,
      errors: MutableList<String>,
  ) {
    when {
      path.isBlank() -> errors += "Architectural layer project path must not be blank."
      path == ":" ->
          errors += "The root project ':' cannot be used as an architectural layer project."
      !absoluteProjectPath.matches(path) ->
          errors +=
              "Architectural layer project path '$path' is malformed; use an absolute top-level Gradle project path such as ':app'."
      !topLevelProjectPath.matches(path) ->
          errors +=
              "Architectural layer project path '$path' is nested; only top-level Gradle projects may define layers."
      path !in projectPaths ->
          errors += "Architectural layer project '$path' does not exist in this build."
    }
  }

  private fun validateDependencyPath(
      layerPath: String,
      dependency: String,
      errors: MutableList<String>,
  ): Boolean {
    return when {
      dependency.isBlank() -> {
        errors += "Architectural layer project '$layerPath' contains a blank dependency path."
        false
      }
      dependency == ":" || !topLevelProjectPath.matches(dependency) -> {
        errors +=
            "Layer dependency '$dependency' in architectural layer project '$layerPath' is malformed; use an absolute top-level Gradle project path such as ':data'."
        false
      }
      else -> true
    }
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

  private fun reachableDependencies(
      layerPath: String,
      dependenciesByPath: Map<String, Set<String>>,
  ): Set<String> {
    val reachable = linkedSetOf<String>()
    fun visit(path: String) {
      dependenciesByPath[path].orEmpty().forEach { dependency ->
        if (dependency in dependenciesByPath && reachable.add(dependency)) visit(dependency)
      }
    }
    visit(layerPath)
    reachable.remove(layerPath)
    return reachable
  }

  private fun findCycle(layers: List<UnresolvedLayer>): List<String>? {
    val knownPaths = layers.mapTo(linkedSetOf()) { it.projectPath }
    val dependencies = layers.associate {
      it.projectPath to
          it.dependencies.filterTo(linkedSetOf()) { dependency ->
            dependency in knownPaths && dependency != it.projectPath
          }
    }
    val visited = mutableSetOf<String>()
    val active = mutableSetOf<String>()
    val path = mutableListOf<String>()
    fun visit(projectPath: String): List<String>? {
      if (projectPath in active) {
        val start = path.indexOf(projectPath)
        return path.subList(start, path.size).toList() + projectPath
      }
      if (!visited.add(projectPath)) return null
      active += projectPath
      path += projectPath
      for (dependency in dependencies[projectPath].orEmpty()) visit(dependency)?.let {
        return it
      }
      path.removeAt(path.lastIndex)
      active -= projectPath
      return null
    }
    for (layer in layers) visit(layer.projectPath)?.let {
      return it
    }
    return null
  }

  private data class UnresolvedLayer(
      val projectPath: String,
      val index: Int,
      val dependencies: Set<String>,
  )
}
