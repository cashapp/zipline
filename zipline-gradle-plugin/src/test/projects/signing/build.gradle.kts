buildscript {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
    google()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
  }
  dependencies {
    classpath("app.cash.zipline:zipline-gradle-plugin:${project.property("ziplineVersion")}")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0")
  }
}

allprojects {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
    google()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
  }
}
