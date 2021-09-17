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
    classpath("com.android.tools.build:gradle:7.0.2")
    classpath("net.ltgt.gradle:gradle-errorprone-plugin:2.0.1")
    classpath("com.vanniktech:gradle-maven-publish-plugin:0.18.0")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.21")
    classpath("org.jetbrains.kotlin:kotlin-serialization:1.5.21")
//    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.30")
//    classpath("org.jetbrains.kotlin:kotlin-serialization:1.5.30")
    classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.5.0")
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
