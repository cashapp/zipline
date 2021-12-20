Zipline Files
-------------

The Gradle plugin has a task that packages compiled JavaScript into a `.zipline` file.

```kotlin
plugins {
  id("app.cash.zipline")
}

kotlin {
  js {
    browser()
    binaries.library()
  }
}

val compileZipline by tasks.creating(ZiplineCompileTask::class) {
  dependsOn(":samples:emoji-search:presenters:compileDevelopmentLibraryKotlinJs")
  inputDir = file("$buildDir/compileSync/main/developmentLibrary/kotlin")
  outputDir = file("$buildDir/zipline")
}

val publish by tasks.getting {
  dependsOn(ziplineFile)
}
```
