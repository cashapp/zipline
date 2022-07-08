# Zipline

This library streamlines using Kotlin/JS libraries from Kotlin/JVM and Kotlin/Native programs. It
makes it fetching code as easy as fetching data:

 * For continuous deployment within mobile apps, just like we do for servers and web apps. It'd be
   simpler to do continuous deploys via the app stores! But that process is too slow and we can't
   guarantee that user’s devices will update immediately.
 * For user-customizable behavior and plugin systems
 * For updating business rules, like pricing or payments
 * For fresh content like games

Zipline works by embedding the [QuickJS JavaScript engine][qjs] in your Kotlin/JVM or Kotlin/Native
program. It's a small and fast JavaScript engine that's well-suited to embedding in applications.

_(Looking for [Duktape Android](#duktape-android)?)_


### Code Example

Let's make a trivia game that has fresh questions every day, even if our users don't update their
apps. We define [our interface][trivia.kt] in `commonMain` so that we can call it from Kotlin/JVM
and implement it in Kotlin/JS.

```kotlin
interface TriviaService : ZiplineService {
  fun games(): List<TriviaGame>
  fun answer(questionId: String, answer: String): AnswerResult
}
```

Next we [implement it][triviaJs.kt] in `jsMain`:

```kotlin
class RealTriviaService : TriviaService {
  // ...
}
```

Let's connect the implementation running in Kotlin/JS to the interface running in Kotlin/JVM. In
`jsMain` we define an [exported function][launchZiplineJs.kt] to bind the implementation:

```kotlin
@JsExport
fun launchZipline() {
  val zipline = Zipline.get()
  zipline.bind<TriviaService>("triviaService", RealTriviaService())
}
```

Now we can start a development server to serve our JavaScript to any running applications that
request it.

```console
$ ./gradlew samples:trivia:trivia-js:jsBrowserProductionRun --info --continuous
```

Note that this Gradle won't ever reach 100%. That's expected; we want the development server to stay
on. Also note that the `--continuous` flag will trigger a re-compile whenever the code changes.

You can see the served application manifest at
[localhost:8080/manifest.zipline.json](http://localhost:8080/manifest.zipline.json). It references
all the code modules for the application.

In `jvmMain` we need write [a program][launchZiplineJvm.kt] that downloads our Kotlin/JS code and
calls it. We use `ZiplineLoader` which handles code downloading, caching, and loading. We create a
`Dispatcher` to run Kotlin/JS on. This must be a single-threaded dispatcher as each Zipline instance
must be confined to a single thread.

```kotlin
suspend fun launchZipline(dispatcher: CoroutineDispatcher): Zipline {
  val manifestUrl = "http://localhost:8080/manifest.zipline.json"
  val loader = ZiplineLoader(dispatcher, OkHttpClient())
  return loader.loadOrFail("trivia", manifestUrl)
}
```

Now we build and run the JVM program to put it all together. Do this in a separate terminal from the
development server!

```console
$ ./gradlew samples:trivia:trivia-host:shadowJar
java -jar samples/trivia/trivia-host/build/libs/trivia-host-1.0.0-SNAPSHOT-all.jar
```


### Interface bridging

Zipline makes it easy to share interfaces with Kotlin/JS. Define an interface in `commonMain`,
implement it in Kotlin/JS, and call it from the host platform. Or do the opposite: implement it on
the host platform and call it from Kotlin/JS.

Bridged interfaces must extend `ZiplineService`, which defines a single `close()` method to release
held resources.

By default, arguments and return values are pass-by-value. Zipline uses kotlinx.serialization to
encode and decode values passed across the boundary.

Interface types that extend from `ZiplineService` are pass-by-reference: the receiver may call
methods on a live instance.

Interface functions may be suspending. Internally Zipline implements `setTimeout()` to make
asynchronous code work as it's supposed to in Kotlin/JS.

Zipline also supports `Flow<T>` as a parameter or return type. This makes it easy to build reactive
systems.


### Fast

One potential bottleneck of embedding JavaScript is waiting for the engine to compile the input
source code. Zipline precompiles JavaScript into efficient QuickJS bytecode to eliminate this
performance penalty.

Another bottleneck is waiting for code to download. Zipline addresses this with support for modular
applications. Each input module (Like Kotlin's standard, serialization, and coroutines libraries)
is downloaded concurrently. Each downloaded module is cached. Modules can also be embedded with the
host application to avoid any downloads if the network is unreachable. If your application module
changes more frequently than your libraries, users only download what's changed.

If you run into performance problems in the QuickJS runtime, Zipline includes a sampling profiler.
You can use this to get a breakdown of how your application spends its CPU time.


### Developer-Friendly

Zipline implements `console.log` by forwarding messages to the host platform. It uses
`android.util.Log` on Android, `java.util.logging` on JVM, and `stdout` on Kotlin/Native.

Zipline integrates Kotlin source maps into QuickJS bytecode. If your process crashes, the stacktrace
will print `.kt` files and line numbers. Even though there's JavaScript underneath, developers don't
need to interface with `.js` files.

After using a bridged interface it must be closed so the peer object can be garbage collected. This
is difficult to get right, so Zipline borrows ideas from [LeakCanary] and aggressively detects
when a `close()` call is missed.


### Requirements

Zipline works on Android 4.3+ (API level 18+), Java 8+, and [Kotlin/Native].

Zipline uses unstable APIs in its implementation and is sensitive to version updates for these
components.

| Component            | Supported Version | Notes                                                                                                 |
| :------------------- | :---------------- | :---------------------------------------------------------------------------------------------------- |
| Kotlin Compiler      | 1.6.21            | Kotlin compiler plugins do not yet have a stable API.                                                 |
| Kotlin Coroutines    | 1.6.1-native-mt   | For `invokeOnClose()`.                                                                                |
| Kotlin Serialization | 1.3.3             | For `decodeFromDynamic()`, `encodeToDynamic()`, `EmptySerializersModule`, and `ContextualSerializer`. |

We intend to use stable APIs as soon as they are available.

We intend to keep Zipline host and runtime releases interoperable so you can upgrade each
independently.

| Host Zipline Version  | Supported Runtime Zipline Versions              |
| --------------------: | :---------------------------------------------- |
|                   0.x | Exact same 0.x version as the host.             |
|                   1.x | Any 1.x version.                                |


### License

    Copyright 2015 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


# Duktape-Android

This project was previously known as Duktape-Android and packaged the
[Duktape](https://duktape.org/) JavaScript engine for Android. The Duktape history is still present
in this repo as are the release tags. Available versions are listed on
[Maven central](https://search.maven.org/artifact/com.squareup.duktape/duktape-android).

[Kotlin/Native]: https://kotlinlang.org/docs/multiplatform-dsl-reference.html#targets
[LeakCanary]: https://square.github.io/leakcanary/
[launchZiplineJs.kt]: samples/trivia/trivia-js/src/jsMain/kotlin/app/cash/zipline/samples/trivia/launchZiplineJs.kt
[launchZiplineJvm.kt]: samples/trivia/trivia-host/src/main/kotlin/app/cash/zipline/samples/trivia/launchZiplineJvm.kt
[qjs]: https://bellard.org/quickjs/
[trivia.kt]: samples/trivia/trivia-shared/src/commonMain/kotlin/app/cash/zipline/samples/trivia/trivia.kt
[triviaJs.kt]: samples/trivia/trivia-js/src/jsMain/kotlin/app/cash/zipline/samples/trivia/triviaJs.kt
