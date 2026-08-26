import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
  kotlin("multiplatform")
  id("com.android.kotlin.multiplatform.library")
  kotlin("plugin.serialization")
  id("app.cash.zipline")
}

kotlin {
  android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "app.cash.zipline.samples.worldclock.presenters"
    minSdk = libs.versions.minSdk.get().toInt()

    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
  }

  js {
    browser()
    binaries.executable()
  }

  iosArm64()
  iosX64()
  iosSimulatorArm64()

  applyDefaultHierarchyTemplate()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api("app.cash.zipline:zipline")
      }
    }
    val hostMain by creating {
      dependsOn(commonMain)
      dependencies {
        implementation("app.cash.zipline:zipline-loader")
        api(libs.okio.core)
      }
    }

    val androidMain by getting {
      dependsOn(hostMain)
      dependencies {
        implementation(libs.okHttp.core)
      }
    }
    val iosMain by getting {
      dependsOn(hostMain)
    }
  }
}

zipline {
  mainFunction.set("app.cash.zipline.samples.worldclock.main")
}

plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().yarnLockAutoReplace = true
}
