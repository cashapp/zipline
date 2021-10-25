import app.cash.zipline.gradle.ZiplineCompileTask

plugins {
  id("app.cash.zipline")
}

val compileHello by tasks.creating(ZiplineCompileTask::class) {
  inputJs = file("$projectDir/hello.js")
  outputZipline = file("$buildDir/zipline/hello.zipline")
}
