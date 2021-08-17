KtBridge
========

This library makes it easy call between Kotlin/JVM and Kotlin/JavaScript. It expands beyond the
built-in QuickJs binding to support custom types.

Configure Kotlin/JS IR
----------------------

KtBridge uses a Kotlin compiler plugin to generate code for calling between the host application and
QuickJS. This requires configuring the build to use the new IR (Intermediate Representation)
compiler. In your root project's `gradle.properties`, add this:

```properties
kotlin.js.compiler=ir
```


Define a Shared API
-------------------

In your `commonMain` directory, create an interface and value types that'll be shared between
platforms. Use an `expect` val to declare a shared global instance.

```kotlin
interface EchoService {
  fun echo(request: EchoRequest): EchoResponse
}

data class EchoRequest(
  val message: String
)

data class EchoResponse(
  val message: String
)

@JsExport
expect val echoServiceBridge: BridgeToJs<EchoService>
```

Define a JsAdapter
------------------

Create a shared `JsAdapter` to encode and decode your parameter types.


Create a Service in Kotlin/JS
-----------------------------

In your `jsMain` directory, create a service object that you'd like to publish to Kotlin/JVM. Then
call `createJsService()` with that object and assign the result value to the `actual` val.

```kotlin
package com.example

import app.cash.quickjs.ktbridge.createBridgeToJs

val echoService: EchoService = ...

@JsExport
actual val echoServiceBridge = createJsService(EchoJsAdapter, echoService)
```

Create a Client in Kotlin/JVM
-----------------------------

In your `jvmMain` directory, call `createJsClient()` to consume the service object.

```kotlin
actual val echoServiceBridge = createJsClient<EchoService>(
  jsAdapter = EchoJsAdapter,
  webpackModuleName = "testing",
)
```

Load your Kotlin/JS module into QuickJS. The service should be ready before you attempt to access it
from the JVM. Call `get()` on the bridge to get a service instance.

```kotlin
val quickJs: QuickJs = ...

val echoService = echoServiceBridge.get(quickJs)
```

Now you can call Kotlin/JS from Kotlin/JVM. Outbound parameters are encoded in the JVM and decoded
in JavaScript.
