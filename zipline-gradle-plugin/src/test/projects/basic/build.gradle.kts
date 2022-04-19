buildscript {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
    google()
  }
  dependencies {
    println("ZIPLINE VERSION = " + project.property("ziplineVersion"))
    classpath("app.cash.zipline:zipline-gradle-plugin:${project.property("ziplineVersion")}")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
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
