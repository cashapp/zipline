import app.cash.zipline.gradle.ZiplineCompileTask

plugins {
  id("app.cash.zipline")
}

val compileZipline by tasks.creating(ZiplineCompileTask::class) {
  inputDir.set(file("$projectDir/jsBuild"))
  outputDir.set(file("$buildDir/zipline"))
}
