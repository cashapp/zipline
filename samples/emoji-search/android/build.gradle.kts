import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  id("com.android.application")
  kotlin("android")
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.example.zipline.emojisearch"
    minSdk = 21
    targetSdk = libs.versions.targetSdk.get().toInt()
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.androidx.compose.get()
  }

  packagingOptions {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

  val samples by signingConfigs.creating {
    storeFile(file("../../samples.keystore"))
    storePassword("javascript")
    keyAlias("javascript")
    keyPassword("javascript")
  }

  buildTypes {
    val debug by getting {
      applicationIdSuffix = ".debug"
      signingConfig = samples
    }
    val release by getting {
      signingConfig = samples
    }
  }
}

dependencies {
  implementation(projects.zipline)
  implementation(projects.ziplineProfiler)
  implementation(projects.samples.emojiSearch.presenters)
  implementation(libs.android.material)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appCompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle)
  implementation(libs.coil.compose)
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  debugImplementation(libs.androidx.compose.ui.tooling)
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.ziplineKotlinPlugin)
  coreLibraryDesugaring(libs.android.desugarJdkLibs)
}
