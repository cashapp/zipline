import com.android.build.gradle.BaseExtension
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

buildscript {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
  dependencies {
    classpath("com.android.tools.build:gradle:4.2.1")
    classpath("net.ltgt.gradle:gradle-errorprone-plugin:2.0.1")
    classpath("com.vanniktech:gradle-maven-publish-plugin:0.14.2")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.20")
    classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.5.0")
  }
}

plugins {
  id("com.github.gmazzo.buildconfig") version "2.1.0" apply false
}

subprojects {
  repositories {
    mavenCentral()
    google()
    jcenter {
      // Required for a dependency of Android lint.
      content {
        includeGroup("org.jetbrains.trove4j")
      }
    }
  }

  plugins.withId("com.android.library") {
    extensions.configure<BaseExtension> {
      lintOptions {
        textReport = true
        textOutput("stdout")
        lintConfig = rootProject.file("lint.xml")

        isCheckDependencies = true
        isCheckTestSources = false // TODO true https://issuetracker.google.com/issues/138247523
        isExplainIssues = false

        // We run a full lint analysis as build part in CI, so skip vital checks for assemble task.
        isCheckReleaseBuilds = false
      }
    }
  }

  plugins.withType<SigningPlugin> {
    extensions.configure<SigningExtension> {
      val signingKey = findProperty("signingKey") as String?
      val signingPassword = ""
      useInMemoryPgpKeys(signingKey, signingPassword)
    }
  }

  tasks.withType(Test::class).configureEach {
    testLogging {
      if (System.getenv("CI") == "true") {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.PASSED)
      }
      exceptionFormat = TestExceptionFormat.FULL
    }
  }

  apply(plugin = "net.ltgt.errorprone")

  dependencies {
    add("errorproneJavac", "com.google.errorprone:javac:9+181-r4173-1")
    add("errorprone", "com.google.errorprone:error_prone_core:2.7.1")
  }

  tasks.withType(JavaCompile::class).configureEach {
    options.errorprone {
      check("MissingFail", CheckSeverity.ERROR)
      check("MissingOverride", CheckSeverity.ERROR)
      check("UnsafeFinalization", CheckSeverity.ERROR)
      check("UnusedException", CheckSeverity.ERROR)
      check("UnusedMethod", CheckSeverity.ERROR)
      check("UnusedNestedClass", CheckSeverity.ERROR)
      check("UnusedVariable", CheckSeverity.ERROR)
    }
  }
}
