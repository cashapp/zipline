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
  linuxX64()
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
        api(Dependencies.kotlinxCoroutines)
        api(project(":zipline"))
        api(Dependencies.okio)
        implementation(Dependencies.kotlinxSerializationJson)
      }
    }
    val engineMain by creating {
      dependencies {
        api(Dependencies.kotlinxCoroutines)
        api(project(":zipline"))
        api(Dependencies.okio)
        implementation(Dependencies.kotlinxSerializationJson)
      }
    }
    val jvmMain by getting {
      dependsOn(engineMain)
      dependencies {
        implementation(Dependencies.okHttp)
        implementation(Dependencies.sqldelightDriverJvm)
        implementation(Dependencies.sqldelightJdbc)
      }
    }
    val androidMain by getting {
      dependencies {
        implementation(Dependencies.sqldelightDriverAndroid)
      }
    }
    targets.withType<KotlinNativeTarget> {
      val main by compilations.getting {
        dependencies {
          implementation(Dependencies.sqldelightDriverNative)
        }
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
    val jvmTest by getting {
      dependsOn(engineTest)
      dependencies {
        implementation(Dependencies.kotlinxCoroutinesTest)
        implementation(Dependencies.okioFakeFileSystem)
      }
    }

    targets.withType<KotlinNativeTarget> {
      val test by compilations.getting
      test.defaultSourceSet.dependsOn(engineTest)
    }
  }
}


android {
  compileSdkVersion(Ext.compileSdk)
}

sqldelight {
  database("Database") {
    packageName = "app.cash.zipline.loader"
  }
}

configure<MavenPublishBaseExtension> {
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGfm")))
}
