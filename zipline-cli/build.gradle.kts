import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("com.palantir.graal")
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

dependencies {
  api(projects.ziplineLoader)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.picocli)

  kapt(Dependencies.picocliCompiler)

  testImplementation(projects.ziplineLoaderTesting)
  testImplementation(Dependencies.junit)
  testImplementation(Dependencies.kotlinxSerializationJson)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.okio)
  testImplementation(Dependencies.okHttpMockWebServer)
}

tasks.shadowJar {
  mergeServiceFiles()
}

graal {
  mainClass("app.cash.zipline.cli.Main")
  outputName("zipline-cli")
  graalVersion(Dependencies.graalvm)
  javaVersion("11")

  option("--no-fallback")
  option("--allow-incomplete-classpath")

  if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    // May be possible without, but autodetection is problematic on Windows 10
    // see https://github.com/palantir/gradle-graal
    // see https://www.graalvm.org/docs/reference-manual/native-image/#prerequisites
    windowsVsVarsPath("C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\VC\\Auxiliary\\Build\\vcvars64.bat")
  }
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
