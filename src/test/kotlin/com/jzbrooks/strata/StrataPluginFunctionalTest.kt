package com.jzbrooks.strata

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class StrataPluginFunctionalTest {
  @TempDir lateinit var testProjectDir: Path

  @Test
  fun `allows same layer direct and transitive dependencies`() {
    fixture(
        standardProjects(),
        kotlinRootBuild(),
        mapOf(
            ":app" to listOf(":app:checkout", ":data:users", ":infrastructure:http"),
            ":data" to listOf(":data:users"),
            ":data:users" to listOf(":infrastructure:http"),
        ),
    )
    val result = run("check")
    assertEquals(TaskOutcome.SUCCESS, result.task(":checkArchitecturalLayers")?.outcome)
    assertContains(result.output, "No forbidden architectural dependencies found")
  }

  @Test
  fun `declaration order does not allow a dependency`() {
    fixture(standardProjects(), kotlinRootBuild(), mapOf(":infrastructure:http" to listOf(":app")))
    val result = runAndFail("checkArchitecturalLayers")
    assertContains(result.output, "Forbidden architectural dependency: :infrastructure -> :app")
    assertContains(result.output, "dependsOn(\":app\")")
    assertContains(result.output, "infrastructure/http/build.gradle.kts")
  }

  @Test
  fun `fails unclassified projects by default and supports IGNORE policy`() {
    fixture(
        listOf(":app", ":utility"),
        """
        plugins { id("com.jzbrooks.strata") }
                   strata { layer(":app") {} }
        """
            .trimIndent(),
    )
    assertContains(runAndFail("help").output, ":utility")
    testProjectDir
        .resolve("build.gradle.kts")
        .writeText(
            """
            plugins { id("com.jzbrooks.strata") }
                       strata {
                         layer(":app") {}
                         unclassifiedProjects.set(com.jzbrooks.strata.UnclassifiedProjectPolicy.IGNORE)
                       }
            """
                .trimIndent()
        )
    assertEquals(
        TaskOutcome.SUCCESS,
        run("checkArchitecturalLayers").task(":checkArchitecturalLayers")?.outcome,
    )
  }

  @Test
  fun `rejects invalid layer and dependency paths`() {
    fixture(
        listOf(":app", ":app:feature", ":data"),
        """
        plugins { id("com.jzbrooks.strata") }
                   strata {
                     layer("app") {}
                     layer(":") {}
                     layer(":app:feature") {}
                     layer(":missing") {}
                     layer(":app") { dependsOn("data", ":unknown", ":app") }
                     layer(":app") {}
                     layer(":data") {}
                   }
        """
            .trimIndent(),
    )
    val output = runAndFail("help").output
    assertContains(output, "'app' is malformed")
    assertContains(output, "root project ':'")
    assertContains(output, "':app:feature' is nested")
    assertContains(output, "':missing' does not exist")
    assertContains(output, "':app' is declared more than once")
    assertContains(output, "Layer dependency 'data'")
    assertContains(output, "unknown layer project ':unknown'")
    assertContains(output, "must not depend on itself")
  }

  @Test
  fun `supports allowances ignored configurations and ignored subtrees`() {
    fixture(
        standardProjects() + listOf(":benchmark", ":benchmark:jmh"),
        kotlinRootBuild(
            """
            ignoreProject(":benchmark")
                           ignoreConfiguration("migration")
                           allow(from = ":infrastructure:http", to = ":data:users", because = "ARCH-123")
            """
                .trimIndent()
        ),
        mapOf(":infrastructure:http" to listOf(":data:users"), ":benchmark:jmh" to listOf(":app")),
        customDependencies = mapOf(":data:users" to ("migration" to ":app")),
    )
    assertEquals(
        TaskOutcome.SUCCESS,
        run("checkArchitecturalLayers").task(":checkArchitecturalLayers")?.outcome,
    )
  }

  @Test
  fun `renders report by layer project`() {
    fixture(standardProjects(), kotlinRootBuild())
    val output = run("architecturalLayersReport").output
    assertContains(output, "1. Layer project: :app")
    assertContains(output, ":app:checkout")
    assertContains(output, "Direct dependencies:\n     :data")
  }

  @Test
  fun `supports the Groovy DSL`() {
    fixture(
        standardProjects(),
        """
        plugins { id 'com.jzbrooks.strata' }
                   strata {
                     layer(':app') { dependsOn ':data' }
                     layer(':data') { dependsOn ':infrastructure' }
                     layer(':infrastructure') {}
                   }
        """
            .trimIndent(),
        mapOf(":app" to listOf(":data:users")),
        groovy = true,
    )
    assertEquals(
        TaskOutcome.SUCCESS,
        run("checkArchitecturalLayers").task(":checkArchitecturalLayers")?.outcome,
    )
  }

  @Test
  fun `reuses configuration cache`() {
    fixture(standardProjects(), kotlinRootBuild())
    run("checkArchitecturalLayers", "--configuration-cache")
    assertContains(
        run("checkArchitecturalLayers", "--configuration-cache").output,
        "Reusing configuration cache",
    )
  }

  private fun fixture(
      projects: List<String>,
      rootBuild: String,
      dependencies: Map<String, List<String>> = emptyMap(),
      groovy: Boolean = false,
      customDependencies: Map<String, Pair<String, String>> = emptyMap(),
  ) {
    testProjectDir
        .resolve("settings.gradle.kts")
        .writeText(
            "rootProject.name = \"fixture\"\n" + projects.joinToString("\n") { "include(\"$it\")" }
        )
    testProjectDir.resolve(if (groovy) "build.gradle" else "build.gradle.kts").writeText(rootBuild)
    projects.forEach { path ->
      val directory =
          testProjectDir
              .resolve(path.removePrefix(":").replace(':', '/'))
              .also(Path::createDirectories)
      val projectDependencies = dependencies[path].orEmpty()
      val custom = customDependencies[path]
      val content =
          if (groovy)
              buildString {
                appendLine("plugins { id 'java-library' }")
                if (projectDependencies.isNotEmpty()) {
                  appendLine("dependencies {")
                  projectDependencies.forEach { appendLine("  implementation project('$it')") }
                  appendLine("}")
                }
              }
          else
              buildString {
                appendLine("plugins { `java-library` }")
                if (custom != null) appendLine("configurations.create(\"${custom.first}\")")
                if (projectDependencies.isNotEmpty() || custom != null) {
                  appendLine("dependencies {")
                  projectDependencies.forEach { appendLine("  implementation(project(\"$it\"))") }
                  if (custom != null)
                      appendLine("  add(\"${custom.first}\", project(\"${custom.second}\"))")
                  appendLine("}")
                }
              }
      directory.resolve(if (groovy) "build.gradle" else "build.gradle.kts").writeText(content)
    }
  }

  private fun kotlinRootBuild(extra: String = "") =
      """plugins { id("com.jzbrooks.strata") }
         strata {
           layer(":app") { dependsOn(":data") }
           layer(":data") { dependsOn(":infrastructure") }
           layer(":infrastructure") {}
           $extra
         }"""
          .trimIndent()

  private fun standardProjects() =
      listOf(
          ":app",
          ":app:checkout",
          ":data",
          ":data:users",
          ":infrastructure",
          ":infrastructure:http",
      )

  private fun run(vararg arguments: String) = runner(arguments.toList()).build()

  private fun runAndFail(vararg arguments: String) = runner(arguments.toList()).buildAndFail()

  private fun runner(arguments: List<String>) =
      GradleRunner.create()
          .withProjectDir(testProjectDir.toFile())
          .withPluginClasspath()
          .withArguments(arguments + listOf("--stacktrace", "--console=plain"))
          .forwardOutput()
}
