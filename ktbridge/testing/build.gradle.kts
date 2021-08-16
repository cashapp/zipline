import org.jetbrains.kotlin.gradle.tasks.KotlinJsDce

plugins {
  kotlin("multiplatform")
}

kotlin {
  jvm()

  js {
    browser {
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(project(":ktbridge"))
      }
    }
  }
}

val processDceJsKotlinJs by tasks.getting(KotlinJsDce::class) {
  keep("quickjs-root-testing.app.cash.quickjs.ktbridge.testing.helloService")
  keep("quickjs-root-testing.app.cash.quickjs.ktbridge.testing.yoService")
}
