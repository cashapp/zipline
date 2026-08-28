import com.android.build.api.variant.HostTestBuilder.Companion.UNIT_TEST_TYPE
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("multiplatform")
  id("com.android.kotlin.multiplatform.library")
  kotlin("plugin.serialization")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("app.cash.sqldelight")
  id("binary-compatibility-validator")
}

kotlin {
  jvm()
  android {
    namespace = "app.cash.zipline.loader"
    compileSdk = libs.versions.compileSdk.get().toInt()
    minSdk = libs.versions.minSdk.get().toInt()

    withDeviceTest {}

    // TODO: Remove when https://issuetracker.google.com/issues/260059413 is resolved.
    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
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
    val androidDeviceTest by getting {
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

androidComponents {
  beforeVariants { variant ->
    variant.hostTests[UNIT_TEST_TYPE]?.enable = false
  }
}

sqldelight {
  databases {
    create("Database") {
      packageName.set("app.cash.zipline.loader.internal.cache")
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty()))
}
