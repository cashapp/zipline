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
  // TODO: figure out how to get sqlite working on native platforms, then restore this.
  if (false) {
    linuxX64()
    macosX64()
    macosArm64()
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(Dependencies.kotlinxCoroutines)
        api(project(":zipline"))
        api(Dependencies.okio)
        implementation(Dependencies.kotlinxSerializationJson)
      }
    }
    val engineMain by creating {
      dependsOn(commonMain)
    }
    val jniMain by creating {
      dependsOn(engineMain)
      dependencies {
        implementation(Dependencies.okHttp)
      }
    }
    val jvmMain by getting {
      dependsOn(jniMain)
      dependencies {
        implementation(Dependencies.sqldelightDriverJvm)
        implementation(Dependencies.sqldelightJdbc)
      }
    }
    val androidMain by getting {
      dependsOn(jniMain)
      dependencies {
        implementation(Dependencies.sqldelightDriverAndroid)
      }
    }
    val nativeMain by creating {
      dependsOn(engineMain)
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
      dependencies {
        implementation(kotlin("test"))
      }
    }
    val jniTest by creating {
      dependsOn(engineTest)
      dependencies {
        implementation(Dependencies.kotlinxCoroutinesTest)
        implementation(Dependencies.okioFakeFileSystem)
      }
    }
    val jvmTest by getting {
      dependsOn(jniTest)
    }
    val androidTest by getting {
      dependsOn(jniTest)
    }

    targets.withType<KotlinNativeTarget> {
      val test by compilations.getting
      test.defaultSourceSet.dependsOn(engineTest)
    }
  }
}


android {
  compileSdkVersion(Ext.compileSdk)

  defaultConfig {
    minSdkVersion(18)
    multiDexEnabled = true
  }

  sourceSets {
    getByName("main") {
      manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
  }
}

sqldelight {
  database("Database") {
    packageName = "app.cash.zipline.loader"
  }
}

configure<MavenPublishBaseExtension> {
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGfm")))
}
