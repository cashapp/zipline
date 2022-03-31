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
  implementation(projects.zipline)
  implementation(projects.ziplineBytecode)
  implementation(projects.ziplineLoader)
  implementation(Dependencies.kotlinGradlePlugin)
  implementation(Dependencies.kotlinxSerializationJson)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  testImplementation(projects.ziplineLoaderTesting)
  testImplementation(Dependencies.junit)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.okHttpMockWebServer)
  testImplementation(Dependencies.truth)
}

buildConfig {
  val compilerPlugin = projects.ziplineKotlinPlugin
  val compilerPluginHosted = projects.ziplineKotlinPluginHosted
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
