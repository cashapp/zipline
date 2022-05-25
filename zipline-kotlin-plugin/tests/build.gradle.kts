plugins {
  kotlin("jvm")
}

dependencies {
  testImplementation(projects.ziplineKotlinPlugin)
  testImplementation(projects.zipline)
  testImplementation(projects.zipline.testing)
  testImplementation(kotlin("test-junit"))
  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(kotlin("compiler-embeddable"))
  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.4.8")
  testImplementation("com.google.truth:truth:1.0")
}
