import app.cash.zipline.gradle.ZiplineCompileTask

plugins {
  id("app.cash.zipline")
}

val compileZipline by tasks.creating(ZiplineCompileTask::class) {
  inputDir = file("$projectDir/jsBuild")
  outputDir = file("$buildDir/zipline")
}
