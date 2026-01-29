import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.TEST_COMPILATION_NAME

plugins {
  kotlin("jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(kotlin("compiler-embeddable"))
  api(libs.okio.core)

  testImplementation(projects.zipline)
  testImplementation(libs.assertk)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test)
}

// In order to simplify writing test schemas, inject the test sources and
// test classpath as properties into the test runtime. This allows testing
// the FIR-based parser on sources written inside the test case. Cool!
tasks.named<Test>(TEST_TASK_NAME).configure {
  val compilation = kotlin.target.compilations.getByName(TEST_COMPILATION_NAME)

  val sources = compilation.defaultSourceSet.kotlin.sourceDirectories.files
  systemProperty("zipline.internal.sources", sources.joinToString(separator = File.pathSeparator))

  val classpath = project.configurations.getByName(compilation.compileDependencyConfigurationName).files
  systemProperty("zipline.internal.classpath", classpath.joinToString(separator = File.pathSeparator))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Empty()
    )
  )
}
