# Change Log

## [Unreleased]


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



# Duktape change log

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
