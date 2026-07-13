# Strata

Strata is a Gradle plugin that enforces dependency direction between ordered logical architecture layers. 
A layer can own several sibling top-level Gradle project roots, and nested projects inherit the layer of their first path segment.

## Kotlin DSL

```kotlin
plugins {
    id("com.jzbrooks.strata") version "0.1.0-SNAPSHOT"
}

architectureLayers {
    layer("application") {
        projects("app", "features")
    }
    layer("domain") {
        projects("domain", "use-cases")
    }
    layer("data") {
        projects("data", "repositories")
    }
    layer("platform") {
        projects("infrastructure", "networking", "database")
    }

    ignoreProject(":benchmark")
    ignoreConfiguration("specialMigrationConfiguration")

    allow(
        from = ":infrastructure:legacy",
        to = ":data:legacy-model",
        because = "Temporary exception tracked by ARCH-123",
    )

    unclassifiedProjects.set(UnclassifiedProjectPolicy.FAIL)
}
```

Apply and configure Strata once in the root project. Layer declaration order defines dependency direction: a project may depend on its own layer or any layer declared to its right. Direct project dependencies declared in all declarable configurations are checked without resolving configurations.

Run `./gradlew checkArchitectureLayers` to validate the build or `./gradlew architectureLayersReport` to inspect the classification. `checkArchitectureLayers` is also attached to the root `check` lifecycle task.

Ignored project paths cover the named project and its descendants. Allowances match only the exact directed source and target paths and require a non-blank justification.

## Groovy DSL

```groovy
plugins {
    id 'com.jzbrooks.strata' version '0.1.0-SNAPSHOT'
}

architectureLayers {
    layer('application') {
        projects 'app', 'features'
    }
    layer('domain') {
        projects 'domain', 'use-cases'
    }
}
```

## License

Strata is available under the [MIT License](LICENSE).
