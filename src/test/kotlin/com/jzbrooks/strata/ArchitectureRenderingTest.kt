package com.jzbrooks.strata

import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ArchitectureRenderingTest {
  private val app = LayerDefinition(":app", 0, setOf(":data"), setOf(":data", ":infrastructure"))
  private val data = LayerDefinition(":data", 1, setOf(":infrastructure"), setOf(":infrastructure"))
  private val infrastructure = LayerDefinition(":infrastructure", 2, emptySet(), emptySet())
  private val layers = listOf(app, data, infrastructure)

  @Test
  fun `same layer descendants are valid in both directions`() {
    val model = model(classification(":app", app), classification(":app:checkout", app))
    assertEquals(
        emptyList(),
        ArchitectureRendering.violations(
            model,
            listOf(edge(":app", ":app:checkout"), edge(":app:checkout", ":app")),
        ),
    )
  }

  @Test
  fun `forbidden diagnostic identifies layer projects`() {
    val model =
        model(
            classification(":infrastructure:http", infrastructure),
            classification(":data:users", data),
        )
    val diagnostic =
        ArchitectureRendering.violations(model, listOf(edge(":infrastructure:http", ":data:users")))
            .single()
    assertContains(diagnostic, "Forbidden architectural dependency: :infrastructure -> :data")
    assertContains(diagnostic, "Source layer project:    :infrastructure")
    assertContains(diagnostic, "Target layer project:    :data")
    assertContains(diagnostic, "dependsOn(\":data\")")
    assertContains(diagnostic, "implementation(project(\":data:users\"))")
  }

  @Test
  fun `report groups nested projects by layer project`() {
    val report =
        ArchitectureRendering.report(
            model(classification(":app", app), classification(":app:checkout", app))
        )
    assertContains(report, "1. Layer project: :app")
    assertContains(report, "     :app:checkout")
    assertContains(report, "Direct dependencies:\n     :data")
  }

  private fun model(vararg classifications: ProjectClassification) =
      ArchitectureModel(
          layers,
          classifications.associateBy { it.projectPath },
          emptySet(),
          emptySet(),
          emptySet(),
      )

  private fun classification(path: String, layer: LayerDefinition) =
      ProjectClassification(path, layer)

  private fun edge(from: String, to: String) =
      DependencyEdge(from, to, "implementation", "${from.removePrefix(":")}/build.gradle.kts")
}
