Zipline Files
-------------

The Gradle plugin has a task that packages compiled JavaScript into a `.zipline` file.

```kotlin
val ziplineFile by tasks.creating(ZiplineFileTask::class) {
  inputJs = "$buildDir/main/productionExecutable/kotlin/zipline-root-testing.js"
  outputZipline = "$buildDir/zipline/testing.zipline"
}

val publish by tasks.getting {
  dependsOn(ziplineFile)
}
```
