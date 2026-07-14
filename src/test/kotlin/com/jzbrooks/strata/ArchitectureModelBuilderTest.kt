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
  fun `classifies a layer project and its descendants together`() {
    val extension = extension()
    extension.layer(":app", Action { it.dependsOn(":data") })
    extension.layer(":data", Action {})

    val model = ArchitectureModelBuilder.build(extension, identities(":app", ":app:main", ":data"))

    assertEquals(":app", model.classificationsByPath.getValue(":app:main").layer.projectPath)
    assertEquals(listOf(":app", ":data"), model.layers.map { it.projectPath })
  }

  @Test
  fun `resolves forward references and transitive dependencies`() {
    val extension = extension()
    extension.layer(":app", Action { it.dependsOn(":data") })
    extension.layer(":infrastructure", Action {})
    extension.layer(":data", Action { it.dependsOn(":infrastructure") })

    val model =
        ArchitectureModelBuilder.build(extension, identities(":app", ":data", ":infrastructure"))

    assertEquals(setOf(":data"), model.layers[0].directDependencies)
    assertEquals(setOf(":data", ":infrastructure"), model.layers[0].effectiveDependencies)
    assertEquals(emptySet(), model.layers[1].effectiveDependencies)
  }

  @Test
  fun `declaration order alone grants no access`() {
    val extension = extension()
    extension.layer(":app", Action {})
    extension.layer(":data", Action {})
    val model = ArchitectureModelBuilder.build(extension, identities(":app", ":data"))
    assertEquals(emptySet(), model.layers.first().effectiveDependencies)
  }

  @Test
  fun `rejects invalid layer project paths`() {
    val extension = extension()
    extension.layer(" ", Action {})
    extension.layer("app", Action {})
    extension.layer(":", Action {})
    extension.layer(":app:feature", Action {})
    extension.layer(":missing", Action {})
    extension.layer(":app", Action {})
    extension.layer(":app", Action {})

    val failure =
        assertFailsWith<GradleException> {
          ArchitectureModelBuilder.build(extension, identities(":app", ":app:feature"))
        }

    assertContains(failure.message.orEmpty(), "must not be blank")
    assertContains(failure.message.orEmpty(), "'app' is malformed")
    assertContains(failure.message.orEmpty(), "root project ':'")
    assertContains(failure.message.orEmpty(), "':app:feature' is nested")
    assertContains(failure.message.orEmpty(), "':missing' does not exist")
    assertContains(failure.message.orEmpty(), "':app' is declared more than once")
  }

  @Test
  fun `rejects malformed unknown self references and deterministic cycles`() {
    val extension = extension()
    extension.layer(":app", Action { it.dependsOn(" ", "data", ":missing", ":app") })
    extension.layer(":data", Action { it.dependsOn(":infrastructure") })
    extension.layer(":infrastructure", Action { it.dependsOn(":data") })

    val failure =
        assertFailsWith<GradleException> {
          ArchitectureModelBuilder.build(extension, identities(":app", ":data", ":infrastructure"))
        }

    assertContains(failure.message.orEmpty(), "blank dependency path")
    assertContains(failure.message.orEmpty(), "Layer dependency 'data'")
    assertContains(failure.message.orEmpty(), "unknown layer project ':missing'")
    assertContains(failure.message.orEmpty(), "':app' must not depend on itself")
    assertContains(
        failure.message.orEmpty(),
        "dependency cycle detected: :data -> :infrastructure -> :data",
    )
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
