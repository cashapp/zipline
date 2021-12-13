plugins {
  `kotlin-dsl`
}

repositories {
  jcenter()
}

kotlin.sourceSets {
  val main by getting {
    kotlin.srcDir("../zipline-library/buildSrc/src/main/kotlin")
  }
}
