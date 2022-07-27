# Troubleshooting

For contributors to Zipline, the following can be exceptions encountered which don't have obvious fixes (yet).

## Build Native Libraries Locally

Zipline requires architecture specific built artifacts of native code.

On macOS, run from Zipline root directory `$ ./.github/workflows/build-mac.sh`.

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

To fix, download the latest published JVM JAR (name should be like `zipline-jvm-0.1.0-square.47.jar`) and extract the files (change the file extension to `.zip` and unzip) in the `jni` directory into `/zipline/zipline/src/jvmMain/resources/jni` in your local Zipline repo on disk.

## Java Architecture Mismatch

Note in the above stacktrace the architecture present in the read path `/jni/x86_64/libquickjs.dylib`. If this architecture doesn't match your computer, then you are using a Java architecture version that doesn't match your computer.

For MX macOS computers, while the project will compile and seem usable, some tests will fail with the above failure when using an `x86_64` Java version on a `aarch64` processor (M1, M2...).

Update your Java version to one that includes `aarch64` support and tests should pass.
