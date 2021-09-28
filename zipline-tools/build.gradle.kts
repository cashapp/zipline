plugins {
  kotlin("jvm")
}

dependencies {
  api(project(":zipline"))
  api(Dependencies.okio)

  testImplementation(Dependencies.truth)
  testImplementation(Dependencies.junit)
}
