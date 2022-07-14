import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import de.undercouch.gradle.tasks.download.Download

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

kotlin {
  jvm {
    withJava()
  }

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(libs.okio.core)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.serialization.json)
      }
    }
  }
}

// Fetch the EdDSA test suite from https://github.com/google/wycheproof/
val fetchWycheproofJson by tasks.creating(Download::class) {
  val wycheproof = file("$buildDir/wycheproof")
  val wycheproofZip = file("$wycheproof/wycheproof.zip")
  val wycheproofJson = file("$wycheproof/eddsa_test.json")

  onlyIf { !wycheproofJson.exists() }
  tempAndMove(true)
  src("https://github.com/google/wycheproof/archive/d8ed1ba95ac4c551db67f410c06131c3bc00a97c.zip")
  dest(wycheproofZip)

  doLast {
    copy {
      from(zipTree(wycheproofZip)
        .matching { include("**/testvectors/eddsa_test.json") }
        .singleFile)
      into(wycheproof)
    }
  }
}

tasks.withType<Test> {
  dependsOn(fetchWycheproofJson)
}

configure<MavenPublishBaseExtension> {
  configure(KotlinMultiplatform(javadocJar = JavadocJar.None()))
}
