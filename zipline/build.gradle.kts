import co.touchlab.cklib.gradle.CompileToBitcode.Language.C
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.internal.file.copy.CopyAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
  id("com.android.kotlin.multiplatform.library")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("co.touchlab.cklib")
  id("com.github.gmazzo.buildconfig")
  id("binary-compatibility-validator")
  id("com.jakewharton.test-distribution")
}

internal abstract class CopyTestingJsTask : Copy() {
  // We need a DirectoryProperty to supply to the Android Gradle Plugin to wire the outputs into the android test resources
  @get:OutputDirectory
  abstract val destination: DirectoryProperty

  fun configureDestination(destination: Provider<Directory>) {
    this.destination = destination
    destinationDir = destination.get().asFile
  }
}

internal val copyTestingJs = tasks.register<CopyTestingJsTask>("copyTestingJs") {
  dependsOn(":zipline-testing:compileDevelopmentLibraryKotlinJs")
  configureDestination(rootProject.layout.buildDirectory.dir("generated/testingJs"))
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
  android {
    namespace = "app.cash.zipline"
    compileSdk = libs.versions.compileSdk.get().toInt()
    minSdk = libs.versions.minSdk.get().toInt()

    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    optimization {
      consumerKeepRules.publish = true
      consumerKeepRules.file("proguard-rules.pro")
    }

    packaging {
      // We get multiple copies of some license files via JNA, which is a transitive dependency of
      // kotlinx-coroutines-test. Don't fail the build on these duplicates.
      resources {
        excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
      }
    }

    // TODO: Remove when https://issuetracker.google.com/issues/260059413 is resolved.
    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
    }
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
      dependencies {
        api(projects.ziplineAndroidNdk)
      }
    }
    val androidDeviceTest by getting {
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

androidComponents {
  onVariants(selector().withBuildType("release")) { variant ->
    variant.sources.resources?.addStaticSourceDirectory("src/androidInstrumentationTest/resources/")
    variant.sources.resources?.addGeneratedSourceDirectory(copyTestingJs, CopyTestingJsTask::destination)
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

fun quickJsVersion(): String {
  return File(projectDir, "native/quickjs/VERSION").readText().trim()
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = JavadocJar.Empty())
  )
}
