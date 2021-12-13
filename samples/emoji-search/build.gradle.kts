import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  id("com.android.application")
  kotlin("android")
}

android {
  compileSdk = Ext.compileSdk

  defaultConfig {
    applicationId = "com.example.zipline.emojisearch"
    minSdk = 21
    targetSdk = Ext.targetSdk
  }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = Ext.composeVersion
  }

  packagingOptions {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

  val samples by signingConfigs.creating {
    storeFile(file("../samples.keystore"))
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
  implementation("app.cash.zipline:zipline")
  implementation(project(":samples:emoji-search:presenters"))
  implementation(Dependencies.androidMaterial)
  implementation(Dependencies.androidxActivityCompose)
  implementation(Dependencies.androidxAppCompat)
  implementation(Dependencies.androidxCoreKtx)
  implementation(Dependencies.androidxLifecycle)
  implementation(Dependencies.coilCompose)
  implementation(Dependencies.composeMaterial)
  implementation(Dependencies.composeUi)
  implementation(Dependencies.composeUiToolingPreview)
  debugImplementation(Dependencies.composeUiTooling)
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, "app.cash.zipline:zipline-kotlin-plugin")
}
