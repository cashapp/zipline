plugins {
  kotlin("jvm")
}

dependencies {
  testImplementation(project(":zipline-kotlin-plugin"))
  testImplementation(project(":zipline"))
  testImplementation(project(":zipline:testing"))
  testImplementation(kotlin("test-junit"))
  testImplementation(Dependencies.kotlinxCoroutinesTest)
  testImplementation(kotlin("compiler-embeddable"))
  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.4.1")
  testImplementation("com.google.truth:truth:1.0")
}
