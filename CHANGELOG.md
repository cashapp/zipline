# Change Log

## [Unreleased]

## [0.9.13] - 2022-12-22

We've changed this project to focus exclusively on executing Kotlin/JS libraries.

We plan to drop support for executing arbitrary JavaScript code. If you've been using either
QuickJS Java (this project's name until September 2021) or Duktape Android (this project's name
until June 2021), those projects remain as git branches but will not receive further updates.

The project's new Maven coordinates are `app.cash.zipline:zipline`.

 * New: `ZiplineScope` is a new mechanism to close pass-by-reference services and flows. Pass a
   `ZiplineScope` to `Zipline.take()` or implement `ZiplineScoped` in a `ZiplineService` to declare
   a scope, then call `ZiplineScope.close()` to close all received services. Note that Flows
   that were passed-by-reference previously needed to be collected exactly once; with this change
   Flows may be collected any number of times.
 * New: Configure the development HTTP server's local port in Gradle:
    ```kotlin
    zipline {
      ...
      httpServerPort.set(22364)
    }
    ```
 * New: Include the service name in `ZiplineApiMismatchException`.
 * Fix: Prevent `.zipline` files from being stored in the HTTP cache. We've added
   'Cache-Control: no-store' request headers to downloads to prevent caching that's redundant
   with ZiplineLoader's cache.
 * Fix: Make `ZiplineService.close()` idempotent for outbound services.


## [0.9.12] - 2022-12-06

 * New: Add `ZiplineFunction.isSuspending`.
 * New: Add events for `ziplineCreate()`, `moduleLoadStart()`, and `moduleLoadEnd()`.


## [0.9.11] - 2022-12-05

 * New: Publish an event when Zipline is closed.
 * Fix: Avoid a linear search through endpoint names.


## [0.9.10] - 2022-11-30

 * New: Add support for `var` and `val` declarations in service interfaces.
 * Fix: Update Gradle task to serve zipline files for compatibility with Gradle 7.6.


## [0.9.9] - 2022-11-16

 * Changed: Roll back Kotlin 1.7.20 to ensure downstream users can still use Compose easily. The plugin remains compatible with 1.7.21.


## [0.9.8] - 2022-11-16

 * New: Support Kotlin 1.7.21.


## [0.9.7] - 2022-11-11

 * Breaking: Change `EventListener` to pass the `Zipline` instance when it is available.
 * Breaking: Introduce `EventListener.applicationLoadSkipped()` when a downloaded manifest hasn't
   changed.
 * New: The development server (`serveDevelopmentZipline`) now notifies code changes via web socket.
   Connect to `/ws` to receive `"reload"` messages.


## [0.9.6] - 2022-10-13

 * Fix: Don't fail with `unexpected call` errors because code was not being rewritten by our Kotlin
   compiler plugin.


## [0.9.5] - 2022-10-06

 * New: Support `@Contextual` as a type annotation on `ZiplineService` parameters and return types.
   This will attempt to find a contextual serializer for the annotated type.
 * Breaking: Rename `LoadedZipline` to `LoadResult`. This allows `ZiplineLoader.load()` to return
   a flow that includes both successes and failures.
 * Breaking: Remove `eval()` support from QuickJs. As a security measure Zipline doesn't support
   evaluating JavaScript at runtime.


## [0.9.4] - 2022-09-07

 * New: Build in a basic HTTP client for Apple platforms.
 * Breaking change: Wrap exceptions thrown in bridged calls with `ZiplineException`. Previously
   these were wrapped in `Exception` which was difficult to catch generically.


## [0.9.3] - 2022-08-23

 * Breaking change: Move `SerializersModule` from a property of `ZiplineLoader` to a parameter in
   `load()` and `loadOnce()`. This enables using a single loader for different applications that
   have different serializers.
 * Breaking change: Make `ZiplineCache` a top-level type. It has its own lifecycle and is best
   managed directly.
 * Breaking change: Pass a `startValue` parameter consistently to event listener callbacks.
 * New: Extension `OkHttpClient.asZiplineHttpClient()` makes it easier to use general-purpose
   Zipline APIs from multiplatform code.


## [0.9.2] - 2022-08-22

 * Breaking change: `ZiplineLoader.load(...)` is no longer suspending.
 * Breaking change: Don't require implementors of `ZiplineHttpClient` to implement URL resolution.
 * Breaking change: Include a default clock implementation on iOS.
 * Breaking change: Require callers to explicitly opt out of signature checks. Pass
   `ManifestVerifier.Companion.NO_SIGNATURE_CHECKS` to use `ZiplineLoader` without code signature
   verification.
 * New: Support ECDSA P-256 for signatures.


## [0.9.1] - 2022-08-18

 * New: Gradle extension `zipline {}` block.
 * New: Compile files in parallel.
 * New: Replace webpack with a dedicated static file server. Use `serveDevelopmentZipline` or
   `serveProductionZipline` to serve an application locally.
 * Fix: Always run Kotlin/JS in strict mode.
 * Upgrade: [Kotlin Serialization 1.4.0][kotlin_serialization_1_4_0].


## [0.9.0] - 2022-08-05

 * New: `ZiplineLoader` is a new module to launch Zipline applications quickly. It supports caching
   including offline launching, code signing, and launching from a flow.
 * New: Zipline's Gradle plugin makes it fast and easy to use build Zipline applications.
 * New: `EventListener` makes it easy to observe with Zipline performance and problems.
 * Upgrade: [Kotlin 1.7.10][kotlin_1_7_10].


## [0.1.0] - 2021-09-30

### Added

* `Zipline` is a new entry point for connecting to Kotlin/JS libraries.
* `ZiplineReference` supports sending service objects across platforms.
* `ZiplineSerializer` supports sending serializers objects across platforms.
* `FlowReference` supports sending `Flow` objects across platforms.
* `InterruptHandler` interrupts executing JavaScript.
* `MemoryUsage` interrogates the state of the JavaScript runtime.

### Changed

* `QuickJs` entry point moved to `app.cash.zipline`.


# QuickJS Java change log

## [0.9.2] - 2021-08-04

### Added

* `compile()` method takes JS source and produces a version-specific bytecode representation.
* `execute()` method takes version-specific bytecode and runs it.


### Changed

* Methods are no longer `synchronized`. If you are performing concurrent access add your own synchronization.


### Fixed

* Self-extract native libraries from JAR when running on the JVM.
* Correct UTF-8 handling of multi-byte graphemes to avoid mismatch between Java's modified UTF-8 and QuickJS's traditional UTF-8.


## [0.9.1] - 2021-07-12

JVM artifact is now available at `app.cash.quickjs:quickjs-jvm` for Linux and Mac OS!

### Fixed

* Handle null argument array which was sometimes supplied to native code instead of a zero-element array.
* Properly track the associated proxy class from native code to avoid a segfault.
* Eliminate a segfault during engine close when cleaning up proxied objects.


## [0.9.0] - 2021-06-14

Backing JS engine change to QuickJS.
Package name is now `app.cash.quickjs`.
Entrypoint is `QuickJs` class.
Maven coordinates are now `app.cash.quickjs:quickjs-android`.
The API and behavior should otherwise be unchanged.


[Unreleased]: https://github.com/cashapp/quickjs-java/compare/0.10.0...HEAD
[0.9.2]: https://github.com/cashapp/quickjs-java/releases/tag/0.9.2
[0.9.1]: https://github.com/cashapp/quickjs-java/releases/tag/0.9.1
[0.9.0]: https://github.com/cashapp/quickjs-java/releases/tag/0.9.0



# Duktape Android change log

## Version 1.4.0 *(2021-06-14)*

 * New: Update to Duktape 2.6.0.
 * Fix: Correct a few JNI reference leaks which may have eventually caused a native crash.
 * Migrated to AndroidX annotations.

## Version 1.3.0 *(2018-08-02)*

 * New: update to Duktape 2.2.1.
 * Fix: update build settings to reduce AAR output size.

## Version 1.2.0 *(2017-09-08)*

 * New: support for arrays of supported types as arguments between Java/JavaScript.
 * New: update to Duktape 1.8.0.
 * Fix: explicitly release temporary JVM objects when returning from calls to Java from JavaScript.
 * Fix: allocate a local frame when binding Java interfaces to allow many methods and arguments.

## Version 1.1.0 *(2016-11-08)*

 * New: support parsing common date formats in JavaScript's "new Date('str')" constructor.
 * Fix: Duktape.evaluate returns null if the implicit return type is unsupported.

## Version 1.0.0 *(2016-09-28)*

 * Renamed Duktape.proxy and Duktape.bind to Duktape.get and Duktape.set.
 * New: support for arguments of type Object between Java/JavaScript.
 * New: support variadic (VarArgs) functions on Java/JavaScript calls.
 * Fix: Make creation and use of a Duktape instance thread-safe.

## Version 0.9.6 *(2016-08-31)*

 * New: call JavaScript methods from Java via proxies.
 * New: update to Duktape 1.5.0.

## Version 0.9.5 *(2016-03-07)*

 * New: call Java methods from JavaScript.
 * New: improved stacktraces. Includes both JavaScript and Java code from the call stack.
 * New: update to Duktape 1.4.0.

## Version 0.9.4 *(2015-11-02)*

 * New: expose JavaScript stacktraces when things fail.

## Version 0.9.3 *(2015-10-07)*

 * Fix: Use global refs in JNI.

## Version 0.9.2 *(2015-10-06)*

 * Fix: Get the timezone from Java rather than using UTC.
 * Fix: Use recommended flags for building.

## Version 0.9.1 *(2015-09-22)*

 * Fix: Correctly propagate errors as exceptions.


## Version 0.9.0 *(2015-09-08)*

Initial release.


[kotlin_1_7_10]: https://github.com/JetBrains/kotlin/releases/tag/v1.7.10
[kotlin_serialization_1_4_0]: https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.4.0
