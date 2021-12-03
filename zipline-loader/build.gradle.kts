
plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}

dependencies {
  api(project(":zipline"))
  api(Dependencies.okio)
  implementation(Dependencies.kotlinxSerializationJson)

  testImplementation(Dependencies.junit)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.kotlinxCoroutinesTest)
  testImplementation(Dependencies.truth)
}
