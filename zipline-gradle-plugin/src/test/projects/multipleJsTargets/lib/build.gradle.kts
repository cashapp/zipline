import app.cash.zipline.gradle.ZiplineCompileTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
  kotlin("multiplatform")
  id("app.cash.zipline")
}

kotlin {
  js("blue") {
    browser()
    binaries.executable()
    attributes {
      attribute(Attribute.of(String::class.java), "blue")
    }
  }

  js("red") {
    browser()
    binaries.executable()
    attributes {
      attribute(Attribute.of(String::class.java), "red")
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
  mainFunction.set("")
  version.set("1.2.3")
}

plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().yarnLockAutoReplace = true
}
