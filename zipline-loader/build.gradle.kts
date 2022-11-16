import com.android.build.gradle.tasks.factory.AndroidUnitTest
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  kotlin("multiplatform")
  id("com.android.library")
  kotlin("plugin.serialization")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("com.squareup.sqldelight")
  id("de.undercouch.download")
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
    val commonMain by getting {
      dependencies {
        api(libs.kotlinx.coroutines.core)
        api(projects.zipline)
        api(libs.okio.core)
        api(libs.sqldelight.runtime)
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
      main.dependencies {
        implementation(libs.sqldelight.driver.native)
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(projects.ziplineLoaderTesting)
        implementation(projects.zipline.testing)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.turbine)
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

    val nativeTest by creating {
      dependencies {
        implementation(projects.ziplineLoaderTesting)
      }
    }
    targets.withType<KotlinNativeTarget> {
      val test by compilations.getting
      test.defaultSourceSet.dependsOn(nativeTest)
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
      java.srcDirs("src/androidTest/kotlin/")
    }
  }
}

sqldelight {
  database("Database") {
    packageName = "app.cash.zipline.loader.internal.cache"
  }
}

// Fetch the EdDSA test suite from https://github.com/google/wycheproof/
val fetchWycheproofJson by tasks.creating(Download::class) {
  val wycheproof = file("$buildDir/wycheproof")
  val wycheproofZip = file("$wycheproof/wycheproof.zip")
  val eddsaTestJson = file("$wycheproof/eddsa_test.json")
  val ecdsaP256Json = file("$wycheproof/ecdsa_secp256r1_sha256_test.json")

  onlyIf { !eddsaTestJson.exists() || !ecdsaP256Json.exists() }
  tempAndMove(true)
  src("https://github.com/google/wycheproof/archive/d8ed1ba95ac4c551db67f410c06131c3bc00a97c.zip")
  dest(wycheproofZip)

  doLast {
    copy {
      from(zipTree(wycheproofZip)
        .matching {
          include("**/testvectors/eddsa_test.json")
          include("**/testvectors/ecdsa_secp256r1_sha256_test.json")
        }.files)
      into(wycheproof)
    }
  }
}

tasks.withType<Test> {
  dependsOn(fetchWycheproofJson)
}

tasks.withType<AndroidUnitTest> {
  enabled = false
}

configure<MavenPublishBaseExtension> {
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGfm")))
}
