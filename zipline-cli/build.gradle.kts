import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("com.github.johnrengelman.shadow")
}

tasks.jar {
  manifest {
    attributes("Automatic-Module-Name" to "app.cash.zipline.cli")
    attributes("Main-Class" to "app.cash.zipline.cli.Main")
  }
}

// resources-templates.
sourceSets {
  main {
    resources.srcDirs("$buildDir/generated/resources-templates")
  }
}

kotlin {
  sourceSets {
    all {
      languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
    }
  }
}

dependencies {
  api(projects.ziplineLoader)
  implementation(libs.okHttp.core)
  implementation(libs.picocli)

  kapt(libs.picocli.compiler)

  testImplementation(projects.ziplineLoaderTesting)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.okio.core)
  testImplementation(libs.okHttp.mockWebServer)
}

tasks.shadowJar {
  mergeServiceFiles()
}

mavenPublishing {
  configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm")))
}

tasks.register<Copy>("copyResourcesTemplates") {
  from("src/main/resources-templates")
  into("$buildDir/generated/resources-templates")
  expand("projectVersion" to "${project.version}")
  filteringCharset = Charsets.UTF_8.toString()
}.let {
  tasks.processResources.dependsOn(it)
  tasks["javaSourcesJar"].dependsOn(it)
}
