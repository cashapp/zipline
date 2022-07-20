import app.cash.zipline.gradle.ZiplineCompileTask

plugins {
  kotlin("multiplatform")
  id("app.cash.zipline")
}

kotlin {
  js() {
    browser()
    binaries.executable()
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation("app.cash.zipline:zipline:${project.property("ziplineVersion")}")
      }
    }
  }
}

tasks.withType(ZiplineCompileTask::class) {
  mainModuleId.set("")
  mainFunction.set("")
  signingKeys.create("key1") {
    privateKeyHex = "ae4737d95df505eac2424000559d072d91db00192756b265a9792007d743cdf7"
  }
  signingKeys.create("key2") {
    privateKeyHex = "6207b6f19c9d7dfa8af31ed5d97891112a877b43b6d8c0f5f1086b170037ba32"
  }
}
