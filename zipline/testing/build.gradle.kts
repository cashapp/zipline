import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
}

kotlin {
  jvm()

  js {
    browser()
    binaries.executable()
  }

  linuxX64()
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
        implementation(project(":zipline"))
      }
    }

    val engineMain by creating {
      dependsOn(commonMain)
    }

    val jvmMain by getting {
      dependsOn(engineMain)
    }

    targets.withType<KotlinNativeTarget> {
      val main by compilations.getting
      main.defaultSourceSet.dependsOn(engineMain)
    }
  }

  targets.all {
    compilations.all {
      val pluginDependency = if (this is AbstractKotlinNativeCompilation) {
        project(":zipline-kotlin-plugin:hosted")
      } else {
        project(":zipline-kotlin-plugin")
      }
      // Naming logic from https://github.com/JetBrains/kotlin/blob/a0e6fb03f0288f0bff12be80c402d8a62b5b045a/libraries/tools/kotlin-gradle-plugin/src/main/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinTargetConfigurator.kt#L519-L520
      val pluginConfigurationName = PLUGIN_CLASSPATH_CONFIGURATION_NAME +
        target.disambiguationClassifier.orEmpty().capitalize() +
        compilationName.capitalize()
      project.dependencies.add(pluginConfigurationName, pluginDependency)
    }
  }
}
