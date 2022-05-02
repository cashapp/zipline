plugins {
  id("com.android.application")
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.example.zipline.octane"
    minSdk = 18
    targetSdk = libs.versions.targetSdk.get().toInt()
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
  implementation(projects.zipline)
  implementation(libs.duktape)
  implementation(libs.okio.core)
}
