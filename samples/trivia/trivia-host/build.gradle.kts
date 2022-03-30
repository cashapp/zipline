import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  id("com.github.johnrengelman.shadow")
}

dependencies {
  implementation(project(":zipline"))
  implementation(project(":zipline-loader"))
  implementation(project(":samples:trivia:trivia-shared"))
  implementation(Dependencies.okHttp)
}

dependencies {
  add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":zipline-kotlin-plugin"))
}

val shadowJar by tasks.getting(ShadowJar::class) {
  manifest {
    attributes("Main-Class" to "app.cash.zipline.samples.trivia.TriviaJvmKt")
  }
}
