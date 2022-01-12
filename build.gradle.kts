import com.android.build.gradle.BaseExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
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
    classpath(Dependencies.mavenPublishGradlePlugin)
    classpath(Dependencies.kotlinGradlePlugin)
    classpath(Dependencies.kotlinSerialization)
    classpath(Dependencies.dokkaGradlePlugin)
    classpath(Dependencies.shadowJarPlugin)
    classpath(Dependencies.cklibGradlePlugin)
    classpath(Dependencies.sqldelightGradle)
  }
}

plugins {
  id("com.github.gmazzo.buildconfig") version "2.1.0" apply false
}

apply(plugin = "com.vanniktech.maven.publish.base")

allprojects {
  group = "app.cash.zipline"
  version = "1.0.0-SNAPSHOT"

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
}

allprojects {
  tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets.configureEach {
      reportUndocumented.set(false)
      skipDeprecated.set(true)
      jdkVersion.set(8)
      perPackageOption {
        matchingRegex.set("app\\.cash\\.zipline\\.internal\\.*")
        suppress.set(true)
      }
    }
    if (name == "dokkaGfm") {
      outputDirectory.set(project.file("${project.rootDir}/docs/0.x"))
    }
  }

  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.DEFAULT)
      signAllPublications()
      pom {
        description.set("Runs Kotlin/JS libraries in Kotlin/JVM and Kotlin/Native programs")
        name.set(project.name)
        url.set("https://github.com/cashapp/zipline/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        developers {
          developer {
            id.set("cashapp")
            name.set("Cash App")
          }
        }
        scm {
          url.set("https://github.com/cashapp/zipline/")
          connection.set("scm:git:https://github.com/cashapp/zipline.git")
          developerConnection.set("scm:git:ssh://git@github.com/cashapp/zipline.git")
        }
      }
    }
  }
}
