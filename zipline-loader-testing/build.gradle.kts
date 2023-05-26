import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
  id("com.squareup.sqldelight")
}

kotlin {
  jvm()

  if (false) {
    linuxX64()
  }
  macosX64()
  macosArm64()
  iosArm64()
  iosX64()
  iosSimulatorArm64()
  tvosArm64()
  tvosSimulatorArm64()
  tvosX64()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(libs.okio.core)
        api(libs.kotlin.test)
        implementation(projects.zipline)
        implementation(projects.ziplineLoader)
        implementation(libs.kotlinx.serialization.json)
      }
    }

    sourceSets.all {
      languageSettings {
        optIn("app.cash.zipline.EngineApi")
      }
    }
  }

  targets.all {
    compilations.all {
      // Naming logic from https://github.com/JetBrains/kotlin/blob/a0e6fb03f0288f0bff12be80c402d8a62b5b045a/libraries/tools/kotlin-gradle-plugin/src/main/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinTargetConfigurator.kt#L519-L520
      val pluginConfigurationName = PLUGIN_CLASSPATH_CONFIGURATION_NAME +
        target.disambiguationClassifier.orEmpty().capitalize() +
        compilationName.capitalize()
      project.dependencies.add(pluginConfigurationName, projects.ziplineKotlinPlugin)
    }
  }
}

sqldelight {
  database("Produce") {
    packageName = "app.cash.zipline.loader.internal.cache.testing"
  }
}
