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
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
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
