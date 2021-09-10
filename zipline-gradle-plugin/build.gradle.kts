import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  id("java-gradle-plugin")
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  implementation(kotlin("gradle-plugin-api"))
}

buildConfig {
  val project = project(":zipline-kotlin-plugin")
  packageName("app.cash.zipline.gradle")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${Ext.kotlinPluginId}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${project.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${project.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${project.version}\"")
}

gradlePlugin {
  plugins {
    create("zipline") {
      id = "app.cash.zipline"
      displayName = "zipline"
      description = "Compiler plugin to generate bridges between platforms"
      implementationClass = "app.cash.zipline.gradle.ZiplinePlugin"
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    GradlePlugin(
      javadocJar = JavadocJar.Empty(), sourcesJar = true
    )
  )
}
