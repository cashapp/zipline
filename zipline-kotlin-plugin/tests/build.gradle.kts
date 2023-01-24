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
  testImplementation(libs.compile.testing)
  testImplementation(libs.truth)
}
