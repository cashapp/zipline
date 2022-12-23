import co.touchlab.cklib.gradle.CompileToBitcode.Language.C
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
  id("com.android.library")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("co.touchlab.cklib")
  id("com.github.gmazzo.buildconfig")
}

val copyTestingJs = tasks.register<Copy>("copyTestingJs") {
  dependsOn(":zipline:testing:compileDevelopmentLibraryKotlinJs")
  destinationDir = buildDir.resolve("generated/testingJs")
  from(projectDir.resolve("testing/build/compileSync/main/developmentLibrary/kotlin"))
}

kotlin {
  android {
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
        implementation(kotlin("test"))
      }
    }

    val engineMain by creating {
      dependsOn(commonMain)
    }
    val engineTest by creating {
      dependsOn(commonTest)
      dependencies {
        implementation(projects.zipline.testing)
      }
    }

    val jniMain by creating {
      dependsOn(engineMain)
      dependencies {
        api(libs.androidx.annotation)
      }
    }
    val androidMain by getting {
      dependsOn(jniMain)
    }
    val jvmMain by getting {
      dependsOn(jniMain)
    }
    val jvmTest by getting {
      dependsOn(engineTest)
      kotlin.srcDir("src/jniTest/kotlin/")
      resources.srcDir(copyTestingJs)
      dependencies {
        implementation(libs.truth)
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.zipline.testing)
      }
    }

    val nativeMain by creating {
      dependsOn(engineMain)
    }
    val nativeTest by creating {
      dependsOn(engineTest)
    }

    targets.withType<KotlinNativeTarget> {
      val main by compilations.getting
      main.defaultSourceSet.dependsOn(nativeMain)

      main.cinterops {
        create("quickjs") {
          header(file("native/quickjs/quickjs.h"))
          header(file("native/common/context-no-eval.h"))
          header(file("native/common/finalization-registry.h"))
          packageName("app.cash.zipline.quickjs")
        }
      }

      val test by compilations.getting
      test.defaultSourceSet.dependsOn(nativeTest)
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

    targets.all {
      compilations.all {
        // Naming logic from https://github.com/JetBrains/kotlin/blob/a0e6fb03f0288f0bff12be80c402d8a62b5b045a/libraries/tools/kotlin-gradle-plugin/src/main/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinTargetConfigurator.kt#L519-L520
        val pluginConfigurationName = PLUGIN_CLASSPATH_CONFIGURATION_NAME +
          target.disambiguationClassifier.orEmpty().capitalize() +
          compilationName.capitalize()
        project.dependencies.add(pluginConfigurationName, projects.ziplineKotlinPlugin)
      }
    }
  }
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
    topLevelConstants = true
  }

  sourceSets.named("engineMain") {
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
        "-Wno-unused-parameter" /* for windows 32*/
      )
    )
  }
}

android {
  compileSdkVersion(libs.versions.compileSdk.get().toInt())

  defaultConfig {
    minSdkVersion(18)
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

    packagingOptions {
      // We get multiple copies of some license files via JNA, which is a transitive dependency of
      // kotlinx-coroutines-test. Don't fail the build on these duplicates.
      exclude("META-INF/AL2.0")
      exclude("META-INF/LGPL2.1")

      // Keep debug symbols to get function names if QuickJS crashes in native code. This grows the
      // release libquickjs.so artifact from 793 KiB to 2.1 MiB. (We expect that release builds of
      // applications will strip these away later.)
      jniLibs.keepDebugSymbols += "**/libquickjs.so"
    }
  }

  sourceSets {
    getByName("main") {
      manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
    getByName("androidTest") {
      java.srcDirs("src/engineTest/kotlin/", "src/jniTest/kotlin/")
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
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(projects.zipline.testing)
}

fun quickJsVersion(): String {
  return File(projectDir, "native/quickjs/VERSION").readText().trim()
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(
      javadocJar = JavadocJar.Dokka("dokkaGfm")
    )
  )
}
