import app.cash.zipline.loader.SignatureAlgorithmId.Ed25519
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
  kotlin("multiplatform")
  id("app.cash.zipline")
}

kotlin {
  js {
    browser()
    binaries.executable()
  }

  if (project.property("enableK2").toString().toBooleanStrict()) {
    targets.configureEach {
      compilations.configureEach {
        compilerOptions.options.languageVersion.set(
          org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0,
        )
      }
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation("app.cash.zipline:zipline:${project.property("ziplineVersion")}")
      }
    }
  }
}

zipline {
  mainModuleId.set("")
  mainFunction.set("")

  signingKeys {
    create("key1") {
      privateKeyHex.set("ae4737d95df505eac2424000559d072d91db00192756b265a9792007d743cdf7")
      algorithmId.set(Ed25519)
    }
    create("key2") {
      privateKeyHex.set("6207b6f19c9d7dfa8af31ed5d97891112a877b43b6d8c0f5f1086b170037ba32")
      algorithmId.set(Ed25519)
    }
  }
}

plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().yarnLockAutoReplace = true
}
