import app.cash.zipline.gradle.ZiplineCompileTask
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

apply(plugin = "app.cash.zipline")

plugins {
  kotlin("multiplatform")
  id("com.android.library")
  kotlin("plugin.serialization")
}

kotlin {
  iosArm64()
  iosX64()
  iosSimulatorArm64()
  macosArm64()
  macosX64()

  android()

  js {
    browser()
    binaries.executable()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation("app.cash.zipline:zipline")
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
        implementation(libs.sqldelight.driver.android)
      }
    }

    val darwinMain by creating {
      dependsOn(hostMain)
    }
    targets.withType<KotlinNativeTarget> {
      val main by compilations.getting
      main.defaultSourceSet.dependsOn(darwinMain)
    }
  }
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "app.cash.zipline.samples.emojisearch.presenters"
}

tasks.withType(ZiplineCompileTask::class) {
  mainFunction.set("app.cash.zipline.samples.emojisearch.preparePresenters")
}
