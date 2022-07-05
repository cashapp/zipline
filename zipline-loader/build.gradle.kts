import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  kotlin("multiplatform")
  id("com.android.library")
  kotlin("plugin.serialization")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("com.squareup.sqldelight")
}

kotlin {
  jvm()
  android {
    publishAllLibraryVariants()
  }
  if (false) {
    linuxX64()
  }
  macosX64()
  macosArm64()
  iosArm64()
  iosX64()
  iosSimulatorArm64()
  tvosArm64()
  tvosSimulatorArm64()
  tvosX64()

  sourceSets {
    all {
      languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
    }

    val commonMain by getting {
      dependencies {
        api(libs.kotlinx.coroutines.core)
        api(projects.zipline)
        api(libs.okio.core)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    val engineMain by creating {
      dependsOn(commonMain)
    }
    val jniMain by creating {
      dependsOn(engineMain)
      dependencies {
        implementation(libs.okHttp.core)
      }
    }
    val jvmMain by getting {
      dependsOn(jniMain)
      dependencies {
        implementation(libs.sqldelight.driver.sqlite)
        implementation(libs.sqlite.jdbc)
      }
    }
    val androidMain by getting {
      dependsOn(jniMain)
      dependencies {
        implementation(libs.sqldelight.driver.android)
      }
    }
    val nativeMain by creating {
      dependsOn(engineMain)
      dependencies {
        implementation(libs.sqldelight.driver.native)
      }
    }
    targets.withType<KotlinNativeTarget> {
      val main by compilations.getting {
        defaultSourceSet.dependsOn(nativeMain)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
      }
    }
    val engineTest by creating {
      dependsOn(commonTest)
      dependencies {
        implementation(projects.ziplineLoaderTesting)
      }
    }
    val jniTest by creating {
      dependsOn(engineTest)
      dependencies {
        implementation(libs.junit)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.okio.fakeFileSystem)
        implementation(libs.sqldelight.driver.sqlite)
        implementation(libs.sqlite.jdbc)
        implementation(libs.turbine)
      }
    }
    val jvmTest by getting {
      dependsOn(jniTest)
      dependencies {
        implementation(projects.zipline.testing)
      }
    }

    targets.withType<KotlinNativeTarget> {
      val test by compilations.getting
      test.defaultSourceSet.dependsOn(engineTest)
    }
  }
}


android {
  compileSdkVersion(libs.versions.compileSdk.get().toInt())

  defaultConfig {
    minSdkVersion(18)
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets {
    getByName("main") {
      manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
    getByName("androidTest") {
      java.srcDirs("src/jniTest/kotlin/")
    }
  }
}

dependencies {
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.okio.fakeFileSystem)
}

sqldelight {
  database("Database") {
    packageName = "app.cash.zipline.loader"
  }
}

configure<MavenPublishBaseExtension> {
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGfm")))
}
