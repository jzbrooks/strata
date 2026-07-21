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
  fun `analysis classifies every edge disposition`() {
    val model =
        model(
            classification(":app", app),
            classification(":app:checkout", app),
            classification(":data", data),
            classification(":infrastructure", infrastructure),
            allowances = setOf(AllowedEdge(":infrastructure", ":data")),
            ignoredConfigurations = setOf("ignored"),
        )
    val report =
        ArchitectureAnalysis.analyze(
            model,
            listOf(
                edge(":app", ":app:checkout"),
                edge(":app", ":data"),
                edge(":infrastructure", ":data"),
                edge(":data", ":app"),
                edge(":data", ":app", "ignored"),
            ),
        )
    assertEquals(
        listOf(
            DependencyDisposition.SAME_LAYER,
            DependencyDisposition.ALLOWED,
            DependencyDisposition.IGNORED,
            DependencyDisposition.FORBIDDEN,
            DependencyDisposition.EXPLICITLY_ALLOWED,
        ),
        report.dependencies.map { it.disposition },
    )
  }

  @Test
  fun `render includes status violations reachable trees cycles shared nodes and classifications`() {
    val model =
        model(
            classification(":app", app),
            classification(":data", data),
            classification(":infrastructure", infrastructure),
        )
    val report =
        ArchitectureAnalysis.analyze(
            model,
            listOf(
                edge(":infrastructure", ":app"),
                edge(":infrastructure", ":data"),
                edge(":app", ":data"),
                edge(":data", ":infrastructure"),
            ),
        )
    val text = ArchitectureRendering.render(report)
    assertContains(text, "Status: FAILED")
    assertContains(text, "Violations: 2")
    assertContains(text, ":infrastructure (:infrastructure) -> :app (:app)")
    assertContains(text, "-> :data [allowed; implementation")
    assertContains(text, "(cycle -> :infrastructure)")
    assertContains(text, "(shared -> :data)")
    assertContains(text, "1. Layer project: :app")
  }

  @Test
  fun `clean report is deterministic across edge order and configurations`() {
    val model = model(classification(":app", app), classification(":data", data))
    val edges =
        listOf(
            edge(":app", ":data", "testImplementation"),
            edge(":app", ":data", "implementation"),
        )
    val first = ArchitectureRendering.render(ArchitectureAnalysis.analyze(model, edges))
    val second = ArchitectureRendering.render(ArchitectureAnalysis.analyze(model, edges.reversed()))
    assertEquals(first, second)
    assertContains(first, "Status: PASSED")
    assertContains(first, "Violations: 0")
  }

  private fun model(
      vararg classifications: ProjectClassification,
      allowances: Set<AllowedEdge> = emptySet(),
      ignoredConfigurations: Set<String> = emptySet(),
  ) =
      ArchitectureModel(
          layers,
          classifications.associateBy { it.projectPath },
          emptySet(),
          ignoredConfigurations,
          allowances,
      )

  private fun classification(path: String, layer: LayerDefinition) =
      ProjectClassification(path, layer)

  private fun edge(from: String, to: String, configuration: String = "implementation") =
      DependencyEdge(from, to, configuration, "${from.removePrefix(":")}/build.gradle.kts")
}
