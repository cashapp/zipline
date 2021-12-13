import com.android.build.gradle.BaseExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.dokka.gradle.DokkaTask

buildscript {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
  dependencies {
    classpath(Dependencies.androidGradlePlugin)
    classpath(Dependencies.errorproneGradlePlugin)
    classpath(Dependencies.mavenPublishGradlePlugin)
    classpath(Dependencies.kotlinGradlePlugin)
    classpath(Dependencies.kotlinSerialization)
    classpath(Dependencies.dokkaGradlePlugin)
    classpath(Dependencies.shadowJarPlugin)
    classpath(Dependencies.cklibGradlePlugin)
  }
}

plugins {
  id("com.github.gmazzo.buildconfig") version "2.1.0" apply false
}

apply(plugin = "com.vanniktech.maven.publish.base")

allprojects {
  repositories {
    mavenCentral()
    google()
  }
}

subprojects {
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
    add("errorproneJavac", Dependencies.errorproneJavac)
    add("errorprone", Dependencies.errorproneCore)
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
