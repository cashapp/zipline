
plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}

dependencies {
  api(project(":zipline"))
  api(Dependencies.okio)
  api(Dependencies.kotlinxSerialization)
  implementation(Dependencies.kotlinxSerializationJson)

  testImplementation(Dependencies.junit)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.kotlinxCoroutinesTest)
  testImplementation(Dependencies.truth)
}
