import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

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
    compilerOptions {
      target.set("es2015")
      moduleKind.set(JsModuleKind.MODULE_UMD)
    }
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

  applyDefaultHierarchyTemplate()

  sourceSets {
    all {
      languageSettings.optIn("kotlin.js.ExperimentalJsExport")
    }

    val commonMain by getting {
      dependencies {
        implementation(projects.zipline)
        implementation(projects.ziplineCryptography)
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

    val nativeMain by getting {
      dependsOn(hostMain)
    }
  }

  // TODO(jessewilson): move this to a common build module, shared with zipline-cryptography.
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

tasks {
  // https://kotlinlang.org/docs/whatsnew19.html#library-linkage-in-kotlin-native
  withType<KotlinNativeCompile>().configureEach {
    compilerOptions {
      freeCompilerArgs.addAll("-Xpartial-linkage-loglevel=ERROR")
    }
  }

  // https://youtrack.jetbrains.com/issue/KT-56025
  // https://youtrack.jetbrains.com/issue/KT-57203
  // Required for K1.
  findByName("jsBrowserProductionWebpack")?.apply {
    dependsOn(named("jsProductionLibraryCompileSync"))
    dependsOn(named("jsDevelopmentLibraryCompileSync"))
  }
  // Required for K1.
  findByName("jsBrowserProductionLibraryPrepare")?.apply {
    dependsOn(named("jsProductionExecutableCompileSync"))
    dependsOn(named("jsDevelopmentLibraryCompileSync"))
  }
  // Required for K2.
  findByName("jsBrowserProductionLibraryDistribution")?.apply {
    dependsOn(named("jsProductionExecutableCompileSync"))
    dependsOn(named("jsDevelopmentLibraryCompileSync"))
  }
}
