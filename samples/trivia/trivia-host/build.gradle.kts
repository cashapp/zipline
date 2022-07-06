import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  id("com.github.johnrengelman.shadow")
}

dependencies {
  implementation(projects.zipline)
  implementation(projects.ziplineLoader)
  implementation(project(":samples:trivia:trivia-shared"))
  implementation(libs.okHttp.core)
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, projects.ziplineKotlinPlugin)
}

val shadowJar by tasks.getting(ShadowJar::class) {
  manifest {
    attributes("Main-Class" to "app.cash.zipline.samples.trivia.triviaJvmKt")
  }
}
