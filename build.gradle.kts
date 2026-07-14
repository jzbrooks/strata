plugins {
  id("org.jetbrains.kotlin.jvm") version "2.4.0"
  id("com.ncorti.ktfmt.gradle") version "0.26.0"
  `java-gradle-plugin`
}

group = "com.jzbrooks.strata"

version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }

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
  testImplementation(kotlin("test"))
  testImplementation(gradleTestKit())
  testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
}

tasks.test { useJUnitPlatform() }
