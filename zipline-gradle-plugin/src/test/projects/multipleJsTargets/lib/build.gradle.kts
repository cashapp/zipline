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
  mainFunction.set("")
  version.set("1.2.3")
}

plugins.withType<YarnPlugin> {
  the<YarnRootExtension>().yarnLockAutoReplace = true
}
