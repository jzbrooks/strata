plugins {
  id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
  id("com.android.lint") version "9.3.0" apply false
  id("com.ncorti.ktfmt.gradle") version "0.26.0" apply false
  id("org.jetbrains.changelog") version "2.5.0"
  id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

version = providers.gradleProperty("VERSION_NAME").toString()
