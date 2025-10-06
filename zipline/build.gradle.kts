import co.touchlab.cklib.gradle.CompileToBitcode.Language.C
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
  id("com.android.library")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("co.touchlab.cklib")
  id("com.github.gmazzo.buildconfig")
  id("binary-compatibility-validator")
  id("com.jakewharton.test-distribution")
}

val copyTestingJs = tasks.register<Copy>("copyTestingJs") {
  dependsOn(":zipline-testing:compileDevelopmentLibraryKotlinJs")
  destinationDir = rootProject.layout.buildDirectory.dir("generated/testingJs").get().asFile
  from(rootDir.resolve("zipline-testing/build/compileSync/js/main/developmentLibrary/kotlin"))
}
tasks.withType<KotlinNativeTest>().configureEach {
  dependsOn(":zipline-testing:compileDevelopmentLibraryKotlinJs")
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.ziplineKotlinPlugin)
  add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.ziplineKotlinPlugin)
}

kotlin {
  androidTarget {
    publishLibraryVariants("release")
  }
  jvm()

  js {
    nodejs()
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

  applyDefaultHierarchyTemplate()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.serialization.core)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.assertk)
        implementation(kotlin("test"))
        implementation(projects.ziplineCryptography)
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.ziplineTesting)
      }
    }

    val hostMain by creating {
      dependsOn(commonMain)
      dependencies {
        api(libs.okio.core)
      }
    }
    val hostTest by creating {
      dependsOn(commonTest)
    }

    val jniMain by creating {
      dependsOn(hostMain)
      dependencies {
        api(libs.androidx.annotation)
      }
    }
    val jniTest by creating {
      dependsOn(hostTest)
    }

    val androidMain by getting {
      dependsOn(jniMain)
    }
    val androidInstrumentedTest by getting {
      dependsOn(jniTest)
      dependencies {
        implementation(libs.assertk)
        implementation(libs.junit)
        implementation(libs.androidx.test.runner)
        implementation(libs.kotlinx.coroutines.test)
        implementation(kotlin("test"))
        implementation(projects.ziplineTesting)
      }
    }
    val jvmMain by getting {
      dependsOn(jniMain)
    }
    val jvmTest by getting {
      dependsOn(jniTest)
      resources.srcDir(copyTestingJs)
      dependencies {
        implementation(libs.junit)
        implementation(projects.ziplineTesting)
      }
    }

    val nativeMain by getting {
      dependsOn(hostMain)
    }
    val nativeTest by getting {
      dependsOn(hostTest)
    }

    targets.withType<KotlinNativeTarget> {
      val main by compilations.getting

      main.cinterops {
        create("quickjs") {
          header(file("native/quickjs/quickjs.h"))
          header(file("native/common/context-no-eval.h"))
          header(file("native/common/finalization-registry.h"))
          header(file("native/common/global-gc.h"))
          packageName("app.cash.zipline.quickjs")
        }
      }

      binaries.withType<Framework> {
        linkerOpts += "-lsqlite3"
      }
    }

    targets.withType<KotlinNativeTargetWithTests<*>> {
      binaries {
        // Configure a separate test where code is compiled in release mode.
        test(setOf(NativeBuildType.RELEASE))
      }
      testRuns {
        create("release") {
          setExecutionSourceFrom(binaries.getByName("releaseTest") as TestExecutable)
        }
      }
    }
  }
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
    topLevelConstants = true
  }

  sourceSets.named("hostMain") {
    packageName("app.cash.zipline")
    buildConfigField("String", "quickJsVersion", "\"${quickJsVersion()}\"")
  }
}

cklib {
  config.kotlinVersion = libs.versions.kotlin.get()
  create("quickjs") {
    language = C
    srcDirs = project.files(file("native/quickjs"), file("native/common"))
    compilerArgs.addAll(
      listOf(
        //"-DDUMP_LEAKS=1", // For local testing ONLY!
        "-DKONAN_MI_MALLOC=1",
        "-DCONFIG_VERSION=\"${quickJsVersion()}\"",
        "-Wno-unknown-pragmas",
        "-ftls-model=initial-exec",
        "-Wno-unused-function",
        "-Wno-error=atomic-alignment",
        "-Wno-sign-compare",
        "-Wno-unused-parameter", /* for windows 32 */
        "-D_Float16=short", // KT-69094
      )
    )
  }
}

android {
  namespace = "app.cash.zipline"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    multiDexEnabled = true

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("proguard-rules.pro")

    ndk {
      abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
    }

    externalNativeBuild {
      cmake {
        arguments("-DANDROID_TOOLCHAIN=clang", "-DANDROID_STL=c++_static")
        cFlags("-fstrict-aliasing", "-DCONFIG_VERSION=\\\"${quickJsVersion()}\\\"")
        cppFlags("-fstrict-aliasing", "-DCONFIG_VERSION=\\\"${quickJsVersion()}\\\"")
      }
    }

    packaging {
      // We get multiple copies of some license files via JNA, which is a transitive dependency of
      // kotlinx-coroutines-test. Don't fail the build on these duplicates.
      resources {
        excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
      }

      // Keep debug symbols to get function names if QuickJS crashes in native code. This grows the
      // release libquickjs.so artifact from 793 KiB to 2.1 MiB. (We expect that release builds of
      // applications will strip these away later.)
      jniLibs.keepDebugSymbols += "**/libquickjs.so"
    }
  }

  // TODO: Remove when https://issuetracker.google.com/issues/260059413 is resolved.
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  sourceSets {
    getByName("androidTest") {
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

fun quickJsVersion(): String {
  return File(projectDir, "native/quickjs/VERSION").readText().trim()
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = JavadocJar.Empty())
  )
}
