package com.jzbrooks.strata

import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ArchitectureRenderingTest {
  private val application =
      LayerDefinition(
          "application",
          0,
          linkedSetOf("app", "features"),
          setOf("domain"),
          setOf("domain", "data", "platform"),
      )
  private val domain =
      LayerDefinition("domain", 1, setOf("domain"), setOf("data"), setOf("data", "platform"))
  private val data =
      LayerDefinition(
          "data",
          2,
          linkedSetOf("data", "repositories"),
          setOf("platform"),
          setOf("platform"),
      )
  private val platform =
      LayerDefinition(
          "platform",
          3,
          linkedSetOf("infrastructure", "networking", "database"),
          emptySet(),
          emptySet(),
      )
  private val layers = listOf(application, domain, data, platform)

  @Test
  fun `same-layer sibling roots are valid in both directions`() {
    val model =
        model(
            classification(":app", "app", application),
            classification(":features:checkout", "features", application),
        )
    val edges =
        listOf(
            edge(":app", ":features:checkout"),
            edge(":features:checkout", ":app"),
        )

    assertEquals(emptyList(), ArchitectureRendering.violations(model, edges))
  }

  @Test
  fun `forbidden diagnostic identifies logical layers and matched roots`() {
    val model =
        model(
            classification(":networking:http", "networking", platform),
            classification(":repositories:users", "repositories", data),
        )

    val diagnostic =
        ArchitectureRendering.violations(
                model,
                listOf(edge(":networking:http", ":repositories:users")),
            )
            .single()

    assertContains(diagnostic, "Forbidden architectural dependency: platform -> data")
    assertContains(diagnostic, "Source project root:     :networking")
    assertContains(diagnostic, "Target project root:     :repositories")
    assertContains(diagnostic, "Declared layer dependencies:\n  (none)")
    assertContains(diagnostic, "dependsOn(\"data\")")
    assertContains(diagnostic, "implementation(project(\":repositories:users\"))")
  }

  @Test
  fun `report groups sibling roots and nested projects by logical layer`() {
    val model =
        model(
            classification(":app", "app", application),
            classification(":features:checkout", "features", application),
            classification(":domain:model", "domain", domain),
        )

    val report = ArchitectureRendering.report(model)

    assertContains(report, "1. application")
    assertContains(report, "     :app")
    assertContains(report, "     :features")
    assertContains(report, "     :features:checkout")
    assertContains(
        report,
        "   Direct dependencies:\n     domain\n\n   May depend on:\n     application\n     domain\n     data\n     platform",
    )
  }

  private fun model(vararg classifications: ProjectClassification): ArchitectureModel =
      ArchitectureModel(
          layers = layers,
          layersByProjectRoot =
              layers.flatMap { layer -> layer.projectRoots.map { it to layer } }.toMap(),
          classificationsByPath = classifications.associateBy { it.projectPath },
          ignoredProjectPaths = emptySet(),
          ignoredConfigurationNames = emptySet(),
          allowances = emptySet(),
      )

  private fun classification(path: String, root: String, layer: LayerDefinition) =
      ProjectClassification(path, root, layer)

  private fun edge(from: String, to: String) =
      DependencyEdge(from, to, "implementation", "${from.removePrefix(":")}/build.gradle.kts")
}
