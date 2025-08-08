import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  id("com.gradleup.shadow")
}

dependencies {
  implementation("app.cash.zipline:zipline")
  implementation("app.cash.zipline:zipline-loader")
  implementation(project(":trivia:trivia-shared"))
  implementation(libs.okHttp.core)
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, "app.cash.zipline:zipline-kotlin-plugin")
}

val shadowJar by tasks.getting(ShadowJar::class) {
  manifest {
    attributes("Main-Class" to "app.cash.zipline.samples.trivia.TriviaJvmKt")
  }
}
