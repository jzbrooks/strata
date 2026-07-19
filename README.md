# Strata

Strata is a Gradle plugin that enforces an explicit dependency graph between project-backed architectural layers.
Each layer is one existing top-level Gradle project. That project and all its descendants belong to the layer automatically.

For example, a build with layers **app**, **data**, & **infrastructure**
can use this plugin to enforce the dependency direction `:app` → `:data` → `:infrastructure`.

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

Apply the bootstrap plugin in `settings.gradle.kts`:

```kotlin
plugins {
    id("com.jzbrooks.strata.collector") version "0.0.1"
}
```

Then apply and configure Strata in the root build:

```kotlin
plugins {
    id("com.jzbrooks.strata") version "0.0.1"
}

strata {
    layer(":app") {
        dependsOn(":data")
    }
    layer(":data") {
        dependsOn(":infrastructure")
    }
    layer(":infrastructure") {}

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

Configure Strata once in the root project. Layer identities and `dependsOn` values must be absolute paths to
top-level projects, including the leading colon. Each layer may depend on its own project subtree and on layers reachable
through its explicit `dependsOn` declarations. Dependencies are transitive, forward references are supported, and
declaration order affects only report display. Cycles and unknown layer paths are configuration errors. Direct project
dependencies declared in all declarable configurations are checked without resolving configurations.

Run `./gradlew checkArchitecturalLayers` to validate the build or `./gradlew architecturalLayersReport` to inspect the classification. `checkArchitecturalLayers` is also attached to the root `check` lifecycle task.

Ignored project paths cover the named project and its descendants.
Allowances match only the exact directed source and target paths and require a non-blank justification.

## Groovy DSL

```groovy
// settings.gradle
plugins {
    id 'com.jzbrooks.strata.collector' version '0.0.1'
}
```

```groovy
plugins {
    id 'com.jzbrooks.strata' version '0.0.1'
}

strata {
    layer(':app') {
        dependsOn ':data'
    }
    layer(':data') { dependsOn ':infrastructure' }
    layer(':infrastructure') {}
}
```

## License

Strata is available under the [MIT License](LICENSE).
