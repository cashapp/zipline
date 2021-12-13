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
  implementation(project(":zipline"))
  implementation(project(":zipline-bytecode"))
  implementation(project(":zipline-loader"))
  implementation(Dependencies.kotlinxSerializationJson)
  implementation(Dependencies.kotlinGradlePlugin)
  implementation(Dependencies.okio)
  testImplementation(Dependencies.junit)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.truth)
}

buildConfig {
  val compilerPlugin = project(":zipline-kotlin-plugin")
  val compilerPluginHosted = project(":zipline-kotlin-plugin-hosted")
  packageName("app.cash.zipline.gradle")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${Ext.kotlinPluginId}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${compilerPlugin.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${compilerPlugin.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_HOSTED_NAME", "\"${compilerPluginHosted.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${compilerPlugin.version}\"")
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
