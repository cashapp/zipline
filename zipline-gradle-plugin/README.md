Zipline Files
-------------

Compile Task
============

The Gradle plugin has a Compile task that packages compiled JavaScript into a `.zipline` file.

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

Download Task
=============

The Gradle plugin has a Download task that downloads the latest Zipline compiled code from the network to a local directory, to be packaged in the shipped app package.

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

val downloadZipline by tasks.creating(ZiplineDownloadTask::class) {
  manifestUrl = "https://your-cdn.com/zipline/alpha-app/latest/manifest.zipline.json"
  outputDir = file("$buildDir/resources/zipline/alpha-app/latest")
}
```
