# Strata

Strata is a Gradle plugin that enforces an explicit dependency graph between logical architectural layers.
A layer can own several sibling top-level Gradle project roots, and nested projects inherit the layer of their first path segment.

For example, a build with layers **app**, **data**, & **infrastructure**
can use this plugin to enforce that `:app` projects do not depend on `:data` or `:infrastructure` projects.

```text
Root project 'example'
+--- Project ':app'
|    +--- Project ':app:checkout'
|    \--- Project ':app:profile'
+--- Project ':data'
|    +--- Project ':data:orders'
|    \--- Project ':data:users'
\--- Project ':infrastructure'
     +--- Project ':infrastructure:featureflags'
     \--- Project ':infrastructure:telemetry'
```

## Kotlin DSL

```kotlin
plugins {
    id("com.jzbrooks.strata") version "0.1.0"
}

strata {
    layer("application") {
        projects("app")
        dependsOn("domain")
    }
    layer("data") {
        projects("data")
        dependsOn("platform")
    }
    layer("platform") {
        projects("infrastructure")
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

Apply and configure Strata once in the root project. Each layer may depend on its own projects and on layers reachable through its explicit `dependsOn` declarations. Dependencies are transitive, forward references are supported, and declaration order affects only report display. Cycles and unknown layer names are configuration errors. Direct project dependencies declared in all declarable configurations are checked without resolving configurations.

Run `./gradlew checkArchitecturalLayers` to validate the build or `./gradlew architectureLayersReport` to inspect the classification. `checkArchitecturalLayers` is also attached to the root `check` lifecycle task.

Ignored project paths cover the named project and its descendants. Allowances match only the exact directed source and target paths and require a non-blank justification.

## Groovy DSL

```groovy
plugins {
    id 'com.jzbrooks.strata' version '0.1.0'
}

strata {
    layer('application') {
        projects 'app'
        dependsOn 'data'
    }
    layer('data') {
        projects 'data'
    }
}
```

## License

Strata is available under the [MIT License](LICENSE).
