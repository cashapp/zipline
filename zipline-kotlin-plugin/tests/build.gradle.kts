plugins {
  kotlin("jvm")
}

dependencies {
  testImplementation(projects.ziplineKotlinPlugin)
  testImplementation(projects.zipline)
  testImplementation(projects.zipline.testing)
  testImplementation(kotlin("test-junit"))
  testImplementation(Dependencies.kotlinReflect)
  testImplementation(Dependencies.kotlinxCoroutinesTest)
  testImplementation(kotlin("compiler-embeddable"))
  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.4.1")
  testImplementation("com.google.truth:truth:1.0")
}
