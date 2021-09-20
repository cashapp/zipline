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
    kotlinCompilerExtensionVersion = "1.0.2"
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
  implementation(project(":zipline"))
  implementation(project(":samples:emoji-search:presenters"))
  implementation("io.coil-kt:coil-compose:1.3.2")
  implementation("androidx.core:core-ktx:1.6.0")
  implementation("androidx.appcompat:appcompat:1.3.1")
  implementation("com.google.android.material:material:1.4.0")
  implementation("androidx.compose.ui:ui:1.0.2")
  implementation("androidx.compose.material:material:1.0.2")
  implementation("androidx.compose.ui:ui-tooling-preview:1.0.2")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
  implementation("androidx.activity:activity-compose:1.4.0-alpha01")
  debugImplementation("androidx.compose.ui:ui-tooling:1.0.2")
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":zipline-kotlin-plugin"))
}
