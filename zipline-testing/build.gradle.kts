import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
}

kotlin {
  jvm()

  js {
    browser()
    // TODO upstream this to ZiplinePlugin
    binaries.library()
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
    all {
      languageSettings.optIn("kotlin.js.ExperimentalJsExport")
    }

    val commonMain by getting {
      dependencies {
        implementation(projects.zipline)
      }
    }

    val hostMain by creating {
      dependsOn(commonMain)
      dependencies {
        implementation(libs.okio.core)
      }
    }

    val jvmMain by getting {
      dependsOn(hostMain)
    }

    val nativeMain by creating {
      dependsOn(hostMain)
    }

    val appleMain by creating {
      dependsOn(nativeMain)
    }
    val macosX64Main by getting { dependsOn(appleMain) }
    val macosArm64Main by getting { dependsOn(appleMain) }
    val iosArm64Main by getting { dependsOn(appleMain) }
    val iosX64Main by getting { dependsOn(appleMain) }
    val iosSimulatorArm64Main by getting { dependsOn(appleMain) }
    val tvosArm64Main by getting { dependsOn(appleMain) }
    val tvosSimulatorArm64Main by getting { dependsOn(appleMain) }
    val tvosX64Main by getting { dependsOn(appleMain) }

    val linuxMain by creating {
      dependsOn(nativeMain)
    }
    val linuxX64Main by getting { dependsOn(linuxMain) }

    targets.withType<KotlinNativeTarget> {
      val main by compilations.getting
      main.defaultSourceSet.dependsOn(nativeMain)
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

// TODO upstream this to ZiplinePlugin
tasks {
  withType(KotlinJsCompile::class.java).all {
    kotlinOptions {
      freeCompilerArgs += listOf("-Xir-per-module")
    }
  }

  // https://youtrack.jetbrains.com/issue/KT-56025
  // https://youtrack.jetbrains.com/issue/KT-57203
  named("jsBrowserProductionWebpack").configure {
    dependsOn(named("jsProductionLibraryCompileSync"))
    dependsOn(named("jsDevelopmentLibraryCompileSync"))
  }
  named("jsBrowserProductionLibraryPrepare").configure {
    dependsOn(named("jsProductionExecutableCompileSync"))
    dependsOn(named("jsDevelopmentLibraryCompileSync"))
  }
}
