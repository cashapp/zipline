import app.cash.zipline.gradle.ZiplineCompileTask

plugins {
  kotlin("multiplatform")
  id("app.cash.zipline")
}

kotlin {
  jvm()

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
    val jvmMain by getting {
      dependencies {
        implementation("app.cash.zipline:zipline:${project.property("ziplineVersion")}")
      }
    }
  }
}

// This task makes the JVM program available to ZiplinePluginTest.
val jvmTestRuntimeClasspath by configurations.getting
val bindAndTakeJvm by tasks.creating(JavaExec::class) {
  classpath = jvmTestRuntimeClasspath
  mainClass.set("app.cash.zipline.tests.BindAndTakeJvmKt")
}

zipline {
  mainFunction.set("")
}
