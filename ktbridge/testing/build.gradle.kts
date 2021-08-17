import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("multiplatform")
}

kotlin {
  jvm()

  js {
    browser {
    }
    binaries.executable()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(project(":ktbridge"))
      }
    }
  }
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":ktbridge:plugin"))
}
