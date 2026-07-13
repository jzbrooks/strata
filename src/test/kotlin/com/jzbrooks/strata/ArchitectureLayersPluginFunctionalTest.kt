package com.jzbrooks.strata

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ArchitectureLayersPluginFunctionalTest {
    @TempDir
    lateinit var testProjectDir: Path

    @Test
    fun `allows sibling roots and nested projects in both directions`() {
        fixture(
            projects = listOf(
                ":app", ":app:navigation", ":app:design-system",
                ":features", ":features:checkout",
                ":domain", ":domain:model",
                ":data", ":data:model", ":repositories", ":repositories:users",
                ":infrastructure", ":networking", ":networking:http", ":database",
            ),
            rootBuild = kotlinRootBuild(),
            dependencies = mapOf(
                ":app" to listOf(":features"),
                ":features" to listOf(":app"),
                ":data" to listOf(":repositories"),
                ":repositories" to listOf(":data"),
                ":networking" to listOf(":infrastructure"),
                ":infrastructure" to listOf(":database"),
                ":app:navigation" to listOf(":features:checkout"),
                ":features:checkout" to listOf(":app:design-system"),
                ":repositories:users" to listOf(":data:model"),
            ),
        )

        val result = run(":check")

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkArchitectureLayers")?.outcome)
        assertContains(result.output, "No forbidden architectural dependencies found")
    }

    @Test
    fun `fails unclassified projects by default and supports IGNORE policy`() {
        fixture(
            listOf(":app", ":utility"),
            """
                plugins { id("com.jzbrooks.strata") }
                architectureLayers {
                    layer("application") { projects("app") }
                }
            """.trimIndent(),
        )

        val failed = runAndFail("checkArchitectureLayers")
        assertContains(failed.output, ":utility")
        assertContains(failed.output, "not assigned to an architectural layer")

        testProjectDir.resolve("build.gradle.kts").writeText(
            """
                plugins { id("com.jzbrooks.strata") }
                architectureLayers {
                    layer("application") { projects("app") }
                    unclassifiedProjects.set(com.jzbrooks.strata.UnclassifiedProjectPolicy.IGNORE)
                }
            """.trimIndent(),
        )
        val ignored = run("checkArchitectureLayers")
        assertEquals(TaskOutcome.SUCCESS, ignored.task(":checkArchitectureLayers")?.outcome)
    }

    @Test
    fun `rejects malformed ignored paths and missing exception endpoints`() {
        fixture(
            listOf(":app"),
            """
                plugins { id("com.jzbrooks.strata") }
                architectureLayers {
                    layer("application") { projects("app") }
                    ignoreProject("benchmark")
                    allow(from = ":app", to = ":missing", because = "Temporary")
                }
            """.trimIndent(),
        )

        val result = runAndFail("help")

        assertContains(result.output, "Ignored project path 'benchmark' is malformed")
        assertContains(result.output, "Architecture exception target ':missing' does not exist")
    }

    @Test
    fun `rejects application to a subproject`() {
        fixture(listOf(":app"), "")
        testProjectDir.resolve("app/build.gradle.kts").writeText(
            "plugins { id(\"com.jzbrooks.strata\") }",
        )

        val result = runAndFail("help")

        assertContains(result.output, "must be applied to the root project only")
    }

    @Test
    fun `fails invalid cross-layer dependencies with rich diagnostics`() {
        fixture(
            projects = standardProjects(),
            rootBuild = kotlinRootBuild(),
            dependencies = mapOf(
                ":repositories:users" to listOf(":domain:model"),
                ":networking:http" to listOf(":repositories:users"),
                ":database" to listOf(":app"),
            ),
        )

        val result = runAndFail("checkArchitectureLayers")

        assertContains(result.output, "Forbidden architectural dependency: data -> domain")
        assertContains(result.output, "Forbidden architectural dependency: platform -> data")
        assertContains(result.output, "Forbidden architectural dependency: platform -> application")
        assertContains(result.output, "Source project root:     :networking")
        assertContains(result.output, "Target project root:     :repositories")
        assertContains(result.output, "networking/http/build.gradle.kts")
    }

    @Test
    fun `supports exact allowances ignored configurations and ignored project subtrees`() {
        val build = kotlinRootBuild(
            extra = """
                ignoreProject(":benchmark")
                ignoreConfiguration("migration")
                allow(
                    from = ":networking:http",
                    to = ":repositories:users",
                    because = "Temporary exception tracked by ARCH-123",
                )
            """.trimIndent(),
        )
        fixture(
            projects = standardProjects() + listOf(":benchmark", ":benchmark:jmh"),
            rootBuild = build,
            dependencies = mapOf(
                ":networking:http" to listOf(":repositories:users"),
                ":benchmark:jmh" to listOf(":app"),
            ),
            customDependencies = mapOf(":repositories:users" to ("migration" to ":domain:model")),
        )

        val result = run("checkArchitectureLayers")

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkArchitectureLayers")?.outcome)
    }

    @Test
    fun `renders informational report by logical layer`() {
        fixture(standardProjects(), kotlinRootBuild())

        val result = run("architectureLayersReport")

        assertContains(result.output, "Architectural layers")
        assertContains(result.output, "1. application")
        assertContains(result.output, "Top-level roots:\n     :app\n     :features")
        assertContains(result.output, ":features:checkout")
        assertContains(result.output, "May depend on:\n     platform")
    }

    @Test
    fun `fails configuration for duplicate normalized roots`() {
        fixture(
            listOf(":app"),
            """
                plugins { id("com.jzbrooks.strata") }
                architectureLayers {
                    layer("application") { projects("app", ":app") }
                }
            """.trimIndent(),
        )

        val result = runAndFail("help")

        assertContains(result.output, "Top-level project root ':app' is declared more than once")
    }

    @Test
    fun `fails configuration for duplicate root assignment and duplicate layer names`() {
        fixture(
            listOf(":app", ":features"),
            """
                plugins { id("com.jzbrooks.strata") }
                architectureLayers {
                    layer("application") { projects("app", "features") }
                    layer("application") { projects("features") }
                }
            """.trimIndent(),
        )

        val result = runAndFail("help")

        assertContains(result.output, "Architectural layer 'application' is declared more than once")
        assertContains(result.output, "Top-level project root ':features' is assigned to multiple architectural layers")
    }

    @Test
    fun `fails configuration for empty layer nested root and blank exception reason`() {
        fixture(
            listOf(":app", ":app:feature"),
            """
                plugins { id("com.jzbrooks.strata") }
                architectureLayers {
                    layer("empty") { }
                    layer("application") { projects(":app:feature") }
                    allow(from = ":app", to = ":app:feature", because = "  ")
                    unclassifiedProjects.set(com.jzbrooks.strata.UnclassifiedProjectPolicy.IGNORE)
                }
            """.trimIndent(),
        )

        val result = runAndFail("help")

        assertContains(result.output, "must declare at least one top-level project root")
        assertContains(result.output, "is not a top-level project root")
        assertContains(result.output, "must include a non-blank justification")
    }

    @Test
    fun `supports the Groovy DSL`() {
        fixture(
            listOf(":app", ":features", ":domain"),
            """
                plugins { id 'com.jzbrooks.strata' }
                architectureLayers {
                    layer('application') {
                        projects 'app', 'features'
                    }
                    layer('domain') {
                        projects 'domain'
                    }
                }
            """.trimIndent(),
            groovy = true,
            dependencies = mapOf(":features" to listOf(":app", ":domain")),
        )

        val result = run("checkArchitectureLayers")

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkArchitectureLayers")?.outcome)
    }

    @Test
    fun `reuses configuration cache`() {
        fixture(standardProjects(), kotlinRootBuild())

        run("checkArchitectureLayers", "--configuration-cache")
        val second = run("checkArchitectureLayers", "--configuration-cache")

        assertContains(second.output, "Reusing configuration cache")
    }

    private fun fixture(
        projects: List<String>,
        rootBuild: String,
        dependencies: Map<String, List<String>> = emptyMap(),
        groovy: Boolean = false,
        customDependencies: Map<String, Pair<String, String>> = emptyMap(),
    ) {
        testProjectDir.resolve("settings.gradle.kts").writeText(
            "rootProject.name = \"fixture\"\n" + projects.joinToString("\n") { "include(\"$it\")" },
        )
        val rootBuildName = if (groovy) "build.gradle" else "build.gradle.kts"
        testProjectDir.resolve(rootBuildName).writeText(rootBuild)
        projects.forEach { path ->
            val directory = testProjectDir.resolve(path.removePrefix(":").replace(':', '/')).also(Path::createDirectories)
            val projectDependencies = dependencies[path].orEmpty()
            val custom = customDependencies[path]
            val content = if (groovy) {
                buildString {
                    appendLine("plugins { id 'java-library' }")
                    if (projectDependencies.isNotEmpty()) {
                        appendLine("dependencies {")
                        projectDependencies.forEach { appendLine("    implementation project('$it')") }
                        appendLine("}")
                    }
                }
            } else {
                buildString {
                    appendLine("plugins { `java-library` }")
                    if (custom != null) appendLine("configurations.create(\"${custom.first}\")")
                    if (projectDependencies.isNotEmpty() || custom != null) {
                        appendLine("dependencies {")
                        projectDependencies.forEach { appendLine("    implementation(project(\"$it\"))") }
                        if (custom != null) appendLine("    add(\"${custom.first}\", project(\"${custom.second}\"))")
                        appendLine("}")
                    }
                }
            }
            directory.resolve(if (groovy) "build.gradle" else "build.gradle.kts").writeText(content)
        }
    }

    private fun kotlinRootBuild(extra: String = "") = """
        plugins { id("com.jzbrooks.strata") }

        architectureLayers {
            layer("application") { projects("app", "features") }
            layer("domain") { projects("domain") }
            layer("data") { projects("data", "repositories") }
            layer("platform") { projects("infrastructure", "networking", "database") }
            $extra
        }
    """.trimIndent()

    private fun standardProjects() = listOf(
        ":app", ":features", ":features:checkout", ":domain", ":domain:model",
        ":data", ":repositories", ":repositories:users",
        ":infrastructure", ":networking", ":networking:http", ":database",
    )

    private fun run(vararg arguments: String) = runner(arguments.toList()).build()

    private fun runAndFail(vararg arguments: String) = runner(arguments.toList()).buildAndFail()

    private fun runner(arguments: List<String>): GradleRunner = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withPluginClasspath()
        .withArguments(arguments + listOf("--stacktrace", "--console=plain"))
        .forwardOutput()
}
