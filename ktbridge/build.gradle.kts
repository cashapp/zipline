import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("multiplatform")
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
}

kotlin {
  jvm {
  }

  js {
    browser {
    }
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(Dependencies.okioMultiplatform)
      }
    }
    val jvmMain by getting {
      dependencies {
        api(Dependencies.androidxAnnotation)
        api(project(":quickjs"))
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(project(":ktbridge:testing"))
        implementation(Dependencies.junit)
        implementation(Dependencies.truth)
      }
    }
  }
}

tasks {
  val jvmTest by getting {
    dependsOn(":ktbridge:testing:jsBrowserProductionWebpack")
  }
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":ktbridge:plugin"))
}
