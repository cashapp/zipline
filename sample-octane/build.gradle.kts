import com.android.build.gradle.BaseExtension

plugins {
  id("com.android.application")
}

extensions.configure<BaseExtension> {
  compileSdkVersion(Ext.compileSdkVersion)

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  defaultConfig {
    applicationId = "com.example.duktape.octane"
    minSdkVersion(18)
    targetSdkVersion(29)
  }

  val samples by signingConfigs.creating {
    storeFile(file("samples.keystore"))
    storePassword("javascript")
    keyAlias("javascript")
    keyPassword("javascript")
  }

  buildTypes {
    val debug by getting {
      applicationIdSuffix(".debug")
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
