plugins {
  id("com.android.application")
}

android {
  compileSdk = Ext.compileSdk

  defaultConfig {
    applicationId = "com.example.zipline.octane"
    minSdk = 18
    targetSdk = Ext.targetSdk
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
  implementation(Dependencies.duktape)
  implementation(Dependencies.okio)
}
