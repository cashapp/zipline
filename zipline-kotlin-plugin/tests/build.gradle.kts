plugins {
  kotlin("jvm")
}

dependencies {
  testImplementation(projects.ziplineKotlinPlugin)
  testImplementation(projects.zipline)
  testImplementation(projects.zipline.testing)
  testImplementation(kotlin("test-junit"))
  testImplementation(libs.kotlin.compile.testing)
  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(kotlin("compiler-embeddable"))
  testImplementation(libs.truth)
}
