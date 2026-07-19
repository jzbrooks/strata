package com.jzbrooks.strata

import java.util.concurrent.ConcurrentLinkedQueue
import org.gradle.api.DefaultTask
import org.gradle.api.IsolatedAction
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

internal interface DependencyEdgesServiceParameters : BuildServiceParameters {
  val bootstrapApplied: Property<Boolean>
}

internal abstract class DependencyEdgesService : BuildService<DependencyEdgesServiceParameters> {
  private val edges = ConcurrentLinkedQueue<DependencyEdge>()

  fun addAll(values: List<DependencyEdge>) {
    edges.addAll(values)
  }

  fun snapshot(): List<DependencyEdge> = edges.toList()
}

@DisableCachingByDefault(
    because = "The task stores dependency metadata in an in-memory build service"
)
internal abstract class CollectProjectDependenciesTask : DefaultTask() {
  @get:Input internal abstract val encodedEdges: ListProperty<String>
  @get:Internal internal abstract val dependencyEdgesService: Property<DependencyEdgesService>

  @TaskAction
  fun collect() {
    dependencyEdgesService.get().addAll(encodedEdges.get().map(::decodeDependencyEdge))
  }

  private fun decodeDependencyEdge(value: String): DependencyEdge {
    val fields = value.split(FIELD_SEPARATOR)
    return DependencyEdge(fields[0], fields[1], fields[2], fields[3])
  }
}

internal class CollectProjectDependenciesAction : IsolatedAction<Project> {
  override fun execute(project: Project) {
    if (project.path == project.isolated.rootProject.path) return
    val service =
        project.gradle.sharedServices.registerIfAbsent(
            DEPENDENCY_EDGES_SERVICE,
            DependencyEdgesService::class.java,
        ) {}
    project.tasks.register(COLLECT_DEPENDENCIES_TASK, CollectProjectDependenciesTask::class.java) {
      it.encodedEdges.set(collectProjectDependencyEdges(project))
      it.dependencyEdgesService.set(service)
      it.usesService(service)
    }
  }

  private fun collectProjectDependencyEdges(project: Project): List<String> {
    val buildFile = relativeBuildFile(project)
    return project.configurations
        .filter { it.isCanBeDeclared }
        .flatMap { configuration ->
          configuration.dependencies.withType(ProjectDependency::class.java).map { dependency ->
            listOf(project.path, dependency.path, configuration.name, buildFile)
                .joinToString(FIELD_SEPARATOR.toString())
          }
        }
  }

  private fun relativeBuildFile(project: Project): String =
      runCatching {
            project.isolated.rootProject.projectDirectory.asFile
                .toPath()
                .relativize(project.buildFile.toPath())
                .toString()
                .replace('\\', '/')
          }
          .getOrDefault(project.buildFile.path)
}
