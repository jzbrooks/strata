plugins {
  id("org.jetbrains.kotlin.jvm") version "2.4.0"
  id("com.ncorti.ktfmt.gradle") version "0.26.0"
  `java-gradle-plugin`
}

group = "com.jzbrooks.strata"

version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }

kotlin { jvmToolchain(17) }

gradlePlugin {
  plugins {
    create("strata") {
      id = "com.jzbrooks.strata"
      implementationClass = "com.jzbrooks.strata.ArchitectureLayersPlugin"
      displayName = "Strata"
      description = "Enforces ordered architectural layers across Gradle projects"
    }
  }
}

dependencies {
  testImplementation(kotlin("test"))
  testImplementation(gradleTestKit())
  testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
}

tasks.test { useJUnitPlatform() }
