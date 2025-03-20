import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile

plugins {
  kotlin("multiplatform")
//  kotlin("plugin.serialization")
}

kotlin {
  jvm()

  wasmWasi {
    binaries.executable()
  }

  applyDefaultHierarchyTemplate()
}

val wasmBinaries by configurations.creating {
  isCanBeConsumed = true
  isCanBeResolved = false
}

artifacts {
  add("wasmBinaries", tasks.named("compileDevelopmentExecutableKotlinWasmWasi"))
}

tasks.withType<KotlinJsCompile>().configureEach {
  compilerOptions {
    // Chasm doesn't support the old Wasm exceptions.
    freeCompilerArgs.addAll("-Xwasm-use-new-exception-proposal")
  }
}
