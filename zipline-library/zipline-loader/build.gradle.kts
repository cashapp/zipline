import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

kotlin {
  jvm()

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
    }
    targets.withType<KotlinNativeTarget> {
      val main by compilations.getting
      main.defaultSourceSet.dependsOn(engineMain)
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
      }
    }
    targets.withType<KotlinNativeTarget> {
      val test by compilations.getting
      test.defaultSourceSet.dependsOn(engineTest)
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGfm")))
}
