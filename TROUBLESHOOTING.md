# Troubleshooting

For contributors to Zipline, the following can be exceptions encountered which don't have obvious fixes (yet).

## Configure Android SDK Location for Gradle/IntelliJ

To let your build resolve Android SDK files, add the following in a `local.properties` file at the root of the Zipline repo directory. If you're using Android Studio to open the Zipline project, this step may not be necessary.

On macOS, you'll replace `{your username}` with your local account username. On other platforms the path will be different.

```
sdk.dir=/Users/{your username}/Library/Android/sdk
```

If you don't have Android SDK downloaded yet, the easiest way is to install Android Studio with default configuration with `brew install android-studio`. It will download the Android SDK to the above location in macOS and setup required usage terms approvals.

## Missing cmake

You may encounter silent failure in the `./.github/workflows/build-mac.sh` from missing `cmake` where this is no build output logs.

On macOS, install with `brew install cmake`.

## Build Native Libraries Locally

Zipline requires architecture specific built artifacts of native code.

On macOS, run from Zipline root directory `./.github/workflows/build-mac.sh`.

## Missing JNI Libraries

```
java.lang.ExceptionInInitializerError
	at app.cash.zipline.Zipline$Companion.create(Zipline.kt:175)
	at app.cash.zipline.Zipline$Companion.create$default(Zipline.kt:170)
	at app.cash.zipline.ConsoleTest.<init>(ConsoleTest.kt:36)
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
  ...
Caused by: java.lang.IllegalStateException: Unable to read /jni/aarch64/libquickjs.dylib from JAR
	at app.cash.zipline.QuickJsNativeLoaderKt.loadNativeLibrary(QuickJsNativeLoader.kt:36)
	at app.cash.zipline.QuickJs.<clinit>(QuickJs.kt:35)
	... 46 more
```

For tests like `app.cash.zipline.ConsoleTest`, failures with the above stacktrace point to missing `.dylib` prebuilt C libraries necessary for using QuickJS from within Kotlin Multiplatform.

To fix, download the latest published JVM JAR (e.g. `jvm-0.9.2.jar`) from the [releases](https://github.com/cashapp/zipline/releases) and extract the files (change the file extension to `.zip` and unzip) in the `jni` directory into `zipline/src/jvmMain/resources/jni` in your local Zipline repo.

## Java Architecture Mismatch

Note in the above stacktrace the architecture present in the read path `/jni/x86_64/libquickjs.dylib`. If this architecture doesn't match your computer, then you are using a Java architecture version that doesn't match your computer.

For MX macOS computers, while the project will compile and seem usable, some tests will fail with the above failure when using an `x86_64` Java version on a `aarch64` processor (M1, M2...).

Update your Java version to one that includes `aarch64` support and tests should pass.
