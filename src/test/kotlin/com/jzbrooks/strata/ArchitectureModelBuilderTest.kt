package com.jzbrooks.strata

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class ArchitectureModelBuilderTest {
  @Test
  fun `normalizes roots and classifies nested sibling projects`() {
    val extension = extension()
    extension.layer("application", Action { it.projects("app", ":features") })
    extension.layer("domain", Action { it.projects("domain") })

    val model =
        ArchitectureModelBuilder.build(
            extension,
            identities(":app", ":app:main", ":features", ":features:checkout", ":domain"),
        )

    assertEquals(
        "application",
        model.classificationsByPath.getValue(":features:checkout").layer.name,
    )
    assertEquals("features", model.classificationsByPath.getValue(":features:checkout").projectRoot)
    assertEquals(listOf("application", "domain"), model.layers.map { it.name })
  }

  @Test
  fun `rejects normalized duplicates in one layer`() {
    val extension = extension()
    extension.layer("application", Action { it.projects("app", ":app") })

    val failure =
        assertFailsWith<GradleException> {
          ArchitectureModelBuilder.build(extension, identities(":app"))
        }

    assertContains(
        failure.message.orEmpty(),
        "declared more than once in architectural layer 'application'",
    )
  }

  @Test
  fun `rejects roots assigned to multiple logical layers`() {
    val extension = extension()
    extension.layer("application", Action { it.projects("app", "features") })
    extension.layer("presentation", Action { it.projects("features") })

    val failure =
        assertFailsWith<GradleException> {
          ArchitectureModelBuilder.build(extension, identities(":app", ":features"))
        }

    assertContains(
        failure.message.orEmpty(),
        "Top-level project root ':features' is assigned to multiple architectural layers",
    )
    assertContains(failure.message.orEmpty(), "application")
    assertContains(failure.message.orEmpty(), "presentation")
  }

  @Test
  fun `rejects nested roots empty layers and duplicate names`() {
    val extension = extension()
    extension.layer("domain", Action { it.projects(":domain:model") })
    extension.layer("domain", Action {})

    val failure =
        assertFailsWith<GradleException> {
          ArchitectureModelBuilder.build(extension, identities(":domain", ":domain:model"))
        }

    assertContains(failure.message.orEmpty(), "is not a top-level project root")
    assertContains(failure.message.orEmpty(), "must declare at least one")
    assertContains(failure.message.orEmpty(), "declared more than once")
  }

  @Test
  fun `ignored project paths cover their subtree`() {
    assertTrue(ArchitectureModelBuilder.isIgnored(":benchmark:jmh", setOf(":benchmark")))
    assertTrue(!ArchitectureModelBuilder.isIgnored(":benchmarking", setOf(":benchmark")))
  }

  private fun extension(): StrataExtension {
    val project = ProjectBuilder.builder().build()
    return project.objects.newInstance(StrataExtension::class.java)
  }

  private fun identities(vararg paths: String) = paths.map {
    ProjectIdentity(it, "${it.removePrefix(":")}/build.gradle.kts")
  }
}
