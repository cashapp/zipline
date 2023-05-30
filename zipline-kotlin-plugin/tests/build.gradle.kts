plugins {
  kotlin("jvm")
}

dependencies {
  testImplementation(projects.ziplineKotlinPlugin)
  testImplementation(projects.zipline)
  testImplementation(projects.zipline.testing)
  testImplementation(kotlin("compiler-embeddable"))
  testImplementation(kotlin("test-junit"))
  testImplementation(libs.assertk)
  testImplementation(libs.kotlin.compile.testing)
  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.kotlinx.coroutines.test)
}
