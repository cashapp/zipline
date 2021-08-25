import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("multiplatform")
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
}

val copyTestingJs = tasks.register<Copy>("copyTestingJs") {
  destinationDir = buildDir.resolve("generated/testingJs")
  from("testing/build/distributions/testing.js")
  dependsOn(":ktbridge:testing:jsBrowserProductionWebpack")
}

kotlin {
  jvm()

  js {
    browser()
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
      resources.srcDir(copyTestingJs)
      dependencies {
        implementation(project(":ktbridge:testing"))
        implementation(Dependencies.junit)
        implementation(Dependencies.truth)
      }
    }
  }
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":ktbridge:plugin"))
}
