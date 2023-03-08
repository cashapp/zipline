buildscript {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
    google()
  }
  dependencies {
    classpath("app.cash.zipline:zipline-gradle-plugin:${project.property("ziplineVersion")}")
    classpath(libs.kotlin.gradle.plugin)
  }
}

allprojects {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
    google()
  }
}
