plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.android.lint")
  id("com.ncorti.ktfmt.gradle")
  id("com.vanniktech.maven.publish")
  `java-gradle-plugin`
}

group = property("GROUP").toString()

version = property("VERSION_NAME").toString()

repositories {
  google()
  mavenCentral()
}

kotlin {
  explicitApi()
  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()
  jvmToolchain(17)
  compilerOptions {
    allWarningsAsErrors.set(true)
    extraWarnings.set(true)
  }
}

gradlePlugin {
  plugins {
    create("strataCollector") {
      id = "com.jzbrooks.strata.collector"
      implementationClass = "com.jzbrooks.strata.StrataCollectorPlugin"
      displayName = "Strata settings bootstrap"
      description = "Enables isolated dependency collection for Strata"
    }
  }
}

dependencies {
  lintChecks("androidx.lint:lint-gradle:1.0.0")
  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}

tasks.test { useJUnitPlatform() }
