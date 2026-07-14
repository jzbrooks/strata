plugins {
  id("org.jetbrains.kotlin.jvm") version "2.4.0"
  id("com.android.lint") version "9.2.1"
  id("com.ncorti.ktfmt.gradle") version "0.26.0"
  `java-gradle-plugin`
}

group = "com.jzbrooks.strata"

version = "0.1.0-SNAPSHOT"

repositories {
  google()
  mavenCentral()
}

kotlin {
  jvmToolchain(17)
  compilerOptions {
    allWarningsAsErrors.set(true)
    extraWarnings.set(true)
  }
}

gradlePlugin {
  plugins {
    create("strata") {
      id = "com.jzbrooks.strata"
      implementationClass = "com.jzbrooks.strata.StrataPlugin"
      displayName = "Strata"
      description =
          "Enforces an explicit architectural layer dependency graph across Gradle projects"
    }
  }
}

dependencies {
  lintChecks("androidx.lint:lint-gradle:1.0.0")
  testImplementation(kotlin("test"))
  testImplementation(gradleTestKit())
  testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}

tasks.test { useJUnitPlatform() }
