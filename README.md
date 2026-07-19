# Strata

Strata is a Gradle plugin that enforces an explicit dependency graph between project-backed architectural layers.
Each layer is one existing top-level Gradle project. That project and all its descendants belong to the layer automatically.

For example, a build with layers **app**, **data**, **design**, & **infrastructure** can use this plugin to
allow `:app` to depend on the sibling `:data` and `:design` layers, which may both depend on `:infrastructure`.

```text
Root project 'example'
+--- Project ':app'
|    +--- Project ':app:checkout'
|    \--- Project ':app:profile'
+--- Project ':data'
|    +--- Project ':data:orders'
|    \--- Project ':data:users'
+--- Project ':design'
|    +--- Project ':design:icons'
|    \--- Project ':design:theme'
\--- Project ':infrastructure'
     +--- Project ':infrastructure:featureflags'
     \--- Project ':infrastructure:telemetry'
```

```mermaid
flowchart TB
    app["<strong>app</strong><br/>:app:checkout<br/>:app:profile"] --> data["<strong>data</strong><br/>:data:orders<br/>:data:users"]
    app --> design["<strong>design</strong><br/>:design:icons<br/>:design:theme"]
    data --> infrastructure["<strong>infrastructure</strong><br/>:infrastructure:featureflags<br/>:infrastructure:telemetry"]
    design --> infrastructure

    classDef appLayer fill:#c79a9f,stroke:#8f5259,color:#35171a,stroke-width:2px
    classDef dataLayer fill:#9aafc1,stroke:#526f89,color:#152532,stroke-width:2px
    classDef designLayer fill:#c6a07f,stroke:#8c623f,color:#352217,stroke-width:2px
    classDef infrastructureLayer fill:#9eb5a6,stroke:#587663,color:#18271e,stroke-width:2px

    class app appLayer
    class data dataLayer
    class design designLayer
    class infrastructure infrastructureLayer
```

## Configuration

Apply the collector plugin in `settings.gradle.kts`. It is used to analyze project dependencies in a project-isolated way.

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
        dependsOn(":data", ":design")
    }
    layer(":data") {
        dependsOn(":infrastructure")
    }
    layer(":design") {
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

For example, this dependency points against the configured layer direction:

```kotlin
// infrastructure/telemetry/build.gradle.kts
dependencies {
    implementation(project(":app:profile"))
}
```

Running `./gradlew checkArchitecturalLayers` fails with an actionable error:

```text
Forbidden architectural dependency: :infrastructure -> :app

Source project:          :infrastructure:telemetry
Source layer project:    :infrastructure
Configuration:           implementation

Target project:          :app:profile
Target layer project:    :app

Declared from:
  infrastructure/telemetry/build.gradle.kts

Likely declaration:
  implementation(project(":app:profile"))

Suggested fixes:
- If architecturally sound, add dependsOn(":app") to layer(":infrastructure").
- Reverse or invert the dependency so ':app' does not own a dependency required by ':infrastructure'.
- Add a narrow, documented exception only when the violation is intentional and temporary.
```

Configure Strata once in the root project. Layer identities and `dependsOn` values must be absolute paths to
top-level projects, including the leading colon.
* Each layer may depend on its own project subtree and on layers reachable through its explicit `dependsOn` declarations.
* Dependencies are transitive, forward references are supported, and declaration order affects only report display.
* Cycles and unknown layer paths are configuration errors.
* Direct project dependencies declared in all declarable configurations are checked without resolving configurations.

Run `./gradlew checkArchitecturalLayers` to validate the build or `./gradlew architecturalLayersReport` to inspect the classification. `checkArchitecturalLayers` is also attached to the root `check` lifecycle task.

Ignored project paths cover the named project and its descendants.
Allowances match only the exact directed source and target paths and require a non-blank justification.

## License

Strata is available under the [MIT License](LICENSE).
