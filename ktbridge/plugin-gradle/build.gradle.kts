plugins {
  id("java-gradle-plugin")
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
}

dependencies {
  implementation(kotlin("gradle-plugin-api"))
}

buildConfig {
  val project = project(":ktbridge:plugin")
  packageName("app.cash.quickjs.ktbridge.plugin")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.extra["kotlinPluginId"]}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${project.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${project.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${project.version}\"")
}

gradlePlugin {
  plugins {
    create("ktBridge") {
      id = rootProject.extra["kotlinPluginId"] as String
      displayName = "KtBridge"
      description = "Generate bridges for calling from Kotlin/JVM to Kotlin/JS"
      implementationClass = "app.cash.quickjs.ktbridge.plugin.KtBridgeGradlePlugin"
    }
  }
}
