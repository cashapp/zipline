Zipline Gradle Plugin
---------------------

Zipline Compilation
===================

The plugin compiles JavaScript files into `.zipline` files. These binary files are faster to launch.

The plugin offers setting the following variables which are used in compilation.

- `mainModuleId`: This is the JS module that your application's entrypoint function is in. If unset, fallback will be to the last key in the topologically sorted `modules` map in the generated ZiplineManifest.
- `mainFunction`: This is the fully qualified function call to start your application, note it does not include the trailing `()` to initiate the call.
- `version`: This string is included in the manifest to identify the version of included code. A reasonable value is the Git SHA of the repo.

```kts
zipline {
  mainFunction.set("my.application.package.path.prepareFunction")
}
```

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
  applicationName = "my-application"
  manifestUrl = "https://your-cdn.com/zipline/alpha-app/latest/manifest.zipline.json"
  downloadDir = file("$buildDir/resources/zipline/alpha-app/latest")
}
```

Testing
=======

This plugin has Gradle integration tests in `ZiplinePluginTest`. The test projects can be loaded
into IntelliJ and executed standalone with Gradle with this setup:

1. Initialize the local test repo. This builds the Zipline plugin and libraries that the test
   projects run against.

    ```
    ./gradlew zipline-gradle-plugin:test
    ```

2. Paste this into the sample project's `gradle.properties`, substituting in the version from
   `build.gradle.kts`:

    ```
    ziplineVersion=1.0.0-SNAPSHOT
    ```

3. Run Gradle.

    ```
    $ cd zipline-gradle-plugin/src/test/projects/basic
    $ ../../../../../gradlew tasks
    ```
