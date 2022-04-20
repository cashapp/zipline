Zipline Gradle Plugin
---------------------

Zipline Compilation
===================

The plugin compiles JavaScript files into `.zipline` files. These binary files are faster to launch.


Webpack Config
==============

The plugin serves compiled `.zipline` files on the Webpack server. This is useful for local
development! You can run the webpack compiler continuously and `.zipline` files will be served to
a `ZiplineLoader`.


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
