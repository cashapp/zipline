plugins {
  kotlin("multiplatform")
  id("com.android.library")
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
}

kotlin {
  android()
  jvm()

  sourceSets {
    val jvmSharedMain by creating {
      dependencies {
        api(Dependencies.androidxAnnotation)
      }
    }
    val androidMain by getting {
      dependsOn(jvmSharedMain)
    }
    val jvmMain by getting {
      dependsOn(jvmSharedMain)
    }
    val jvmTest by getting {
      dependencies {
        api(Dependencies.junit)
      }
      kotlin.srcDir("src/jvmSharedTest/kotlin/")
    }
  }
}

android {
  compileSdkVersion(Ext.compileSdkVersion)

  defaultConfig {
    minSdkVersion(18)

    testInstrumentationRunner("androidx.test.runner.AndroidJUnitRunner")

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
      java.srcDir("src/jvmSharedTest/kotlin/")
      resources.srcDir("src/androidInstrumentationTest/resources/")
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
}

fun quickJsVersion(): String {
  return File(projectDir, "src/native/quickjs/VERSION").readText().trim()
}
