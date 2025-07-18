import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("multiplatform")
  id("com.android.library")
  kotlin("plugin.serialization")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("app.cash.sqldelight")
  id("binary-compatibility-validator")
}

kotlin {
  jvm()
  androidTarget {
    publishLibraryVariants("release")
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

  applyDefaultHierarchyTemplate()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(libs.kotlinx.coroutines.core)
        api(projects.zipline)
        api(libs.okio.core)
        api(libs.sqldelight.runtime)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    val jniMain by creating {
      dependsOn(commonMain)
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
    val nativeMain by getting {
      dependencies {
        implementation(libs.sqldelight.driver.native)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(libs.assertk)
        implementation(kotlin("test"))
        implementation(projects.ziplineLoaderTesting)
        implementation(projects.ziplineTesting)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.turbine)
      }
    }
    val androidInstrumentedTest by getting {
      dependsOn(commonTest)
    }
    val jniTest by creating {
      dependsOn(commonTest)
      dependencies {
        implementation(libs.junit)
        implementation(libs.okHttp.mockWebServer)
        implementation(libs.okio.fakeFileSystem)
        implementation(libs.sqldelight.driver.sqlite)
        implementation(libs.sqlite.jdbc)
      }
    }
    val jvmTest by getting {
      dependsOn(jniTest)
      dependencies {
        implementation(projects.ziplineLoaderTesting)
      }
    }

    val nativeTest by getting {
      dependencies {
        implementation(projects.ziplineLoaderTesting)
      }
    }
  }
}


android {
  namespace = "app.cash.zipline.loader"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    multiDexEnabled = true
  }

  // TODO: Remove when https://issuetracker.google.com/issues/260059413 is resolved.
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  sourceSets {
    getByName("androidTest") {
      java.srcDirs("src/androidInstrumentedTest/kotlin/")
    }
  }
}

sqldelight {
  databases {
    create("Database") {
      packageName.set("app.cash.zipline.loader.internal.cache")
    }
  }
}

afterEvaluate {
  tasks.named("compileDebugUnitTestKotlinAndroid") {
    enabled = false
  }
  tasks.named("compileReleaseUnitTestKotlinAndroid") {
    enabled = false
  }
}

configure<MavenPublishBaseExtension> {
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty()))
}
