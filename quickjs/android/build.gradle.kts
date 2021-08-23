import com.android.build.gradle.BaseExtension

plugins {
  id("com.android.library")
  kotlin("android")
  id("com.vanniktech.maven.publish")
  id("org.jetbrains.dokka")
}

extensions.configure<BaseExtension> {
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
      java.srcDir("../common/java/")
    }
    val androidTest by getting {
      java.srcDir("../common/test/")
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
      path = file("CMakeLists.txt")
    }
  }
}

dependencies {
  api(Dependencies.androidxAnnotation)

  androidTestImplementation(Dependencies.androidxTestRunner)
}

fun quickJsVersion(): String {
  return File(projectDir, "../common/native/quickjs/VERSION").readText().trim()
}
