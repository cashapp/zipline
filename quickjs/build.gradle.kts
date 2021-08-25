import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("multiplatform")
  id("com.android.library")
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
}

abstract class VersionWriterTask : DefaultTask() {
  @InputFile
  val versionFile = project.file("src/native/quickjs/VERSION")

  @OutputDirectory
  val outputDir = project.layout.buildDirectory.file("generated/version/")

  @TaskAction
  fun stuff() {
    val version = versionFile.readText().trim()

    val outputFile = outputDir.get().asFile.resolve("app/cash/quickjs/version.kt")
    outputFile.parentFile.mkdirs()
    outputFile.writeText("""
      |package app.cash.quickjs
      |
      |internal const val quickJsVersion = "$version"
      |""".trimMargin())
  }
}
val versionWriterTaskProvider = tasks.register("writeVersion", VersionWriterTask::class)

val copyTestingJs = tasks.register<Copy>("copyTestingJs") {
  destinationDir = buildDir.resolve("generated/testingJs")
  from(projectDir.resolve("testing/build/distributions/testing.js"))
  dependsOn(":quickjs:testing:jsBrowserProductionWebpack")
}

kotlin {
  android()
  jvm()
  js {
    nodejs()
  }

  sourceSets {
    val commonMain by getting {
      kotlin.srcDir(versionWriterTaskProvider)
      dependencies {
        api(Dependencies.okioMultiplatform)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
      }
    }

    val jniMain by creating {
      dependencies {
        api(Dependencies.androidxAnnotation)
        api(Dependencies.kotlinReflect)
      }
    }
    val androidMain by getting {
      dependsOn(jniMain)
    }
    val jvmMain by getting {
      dependsOn(jniMain)
    }
    val jvmTest by getting {
      kotlin.srcDir("src/jniTest/kotlin/")
      resources.srcDir(copyTestingJs)
      dependencies {
        implementation(Dependencies.truth)
        implementation(project(":quickjs:testing"))
      }
    }
  }
}

android {
  compileSdkVersion(Ext.compileSdkVersion)

  defaultConfig {
    minSdkVersion(18)

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += Ext.ndkAbiFilters
    }

    externalNativeBuild {
      cmake {
        arguments("-DANDROID_TOOLCHAIN=clang", "-DANDROID_STL=c++_static")
        cFlags("-fstrict-aliasing", "-DCONFIG_VERSION=\\\"${quickJsVersion()}\\\"")
        cppFlags("-fstrict-aliasing", "-DCONFIG_VERSION=\\\"${quickJsVersion()}\\\"")
      }
    }
  }

  sourceSets {
    val main by getting {
      manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
    val androidTest by getting {
      java.srcDir("src/jniTest/kotlin/")
      resources.srcDir("src/androidInstrumentationTest/resources/")
      resources.srcDir(copyTestingJs)
    }
  }

  // The above `resources.srcDir(copyTestingJs)` code is supposed to automatically add a task
  // dependency, but it doesn't. So we add it ourselves using this nonsense.
  afterEvaluate {
    libraryVariants.onEach { libraryVariant ->
      libraryVariant.testVariant?.processJavaResourcesProvider?.configure {
        dependsOn(copyTestingJs)
      }
    }
  }

  buildTypes {
    val release by getting {
      externalNativeBuild {
        cmake {
          arguments("-DCMAKE_BUILD_TYPE=MinSizeRel")
          cFlags("-g0", "-Os", "-fomit-frame-pointer", "-DNDEBUG", "-fvisibility=hidden")
          cppFlags("-g0", "-Os", "-fomit-frame-pointer", "-DNDEBUG", "-fvisibility=hidden")
        }
      }
    }
    val debug by getting {
      externalNativeBuild {
        cmake {
          cFlags("-g", "-DDEBUG", "-DDUMP_LEAKS")
          cppFlags("-g", "-DDEBUG", "-DDUMP_LEAKS")
        }
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/androidMain/CMakeLists.txt")
    }
  }
}

dependencies {
  androidTestImplementation(Dependencies.junit)
  androidTestImplementation(Dependencies.androidxTestRunner)
  androidTestImplementation(Dependencies.truth)
  androidTestImplementation(project(":quickjs:testing"))

  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":quickjs-kotlin-plugin"))
}

fun quickJsVersion(): String {
  return File(projectDir, "src/native/quickjs/VERSION").readText().trim()
}
