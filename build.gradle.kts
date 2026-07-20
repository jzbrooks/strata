plugins {
  id("org.jetbrains.kotlin.jvm") version "2.4.0" apply false
  id("com.android.lint") version "9.2.1" apply false
  id("com.ncorti.ktfmt.gradle") version "0.26.0" apply false
  id("org.jetbrains.changelog") version "2.5.0"
  id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

changelog.path.set("changelog.md")
