plugins {
  kotlin("multiplatform")
  id("com.android.library")
  id("app.cash.zipline")
}

kotlin {
  androidTarget()

  js {
    browser()
    binaries.executable()
  }

  if (project.property("enableK2").toString().toBooleanStrict()) {
    targets.configureEach {
      compilations.configureEach {
        compilerOptions.options.languageVersion.set(
          org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0,
        )
      }
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation("app.cash.zipline:zipline:${project.property("ziplineVersion")}")
      }
    }
  }
}

android {
  namespace = "app.cash.zipline.tests.android"
  compileSdk = libs.versions.compileSdk.get().toInt()
}
