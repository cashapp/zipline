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
platforms.

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
```

Define a JsAdapter
------------------

Create a shared `JsAdapter` to encode and decode your parameter types.


Set a Service in Kotlin/JS
--------------------------

In your `jsMain` directory, create a service object that you'd like to publish to Kotlin/JVM. Then
call `ktBridge.set()` with that object. Note that you must explicitly specify the type parameter.

```kotlin
package com.example

import app.cash.zipline.ktbridge.ktBridge

val echoService: EchoService = ...

fun prepareJsBridges() {
  ktBridge.set<EchoService>("helloService", EchoJsAdapter, JsEchoService("hello"))
}
```

Get a Client in Kotlin/JVM
--------------------------

Load your Kotlin/JS module into QuickJS. The service should be ready before you attempt to access it
from the JVM. Then get the service from a `KtBridge` instance:

```kotlin
val quickJs: QuickJs = ...
val ktBridge: KtBridge = createKtBridge(quickJs, "testing")
val helloService: EchoService = ktBridge.get("helloService", EchoJsAdapter)
```

Now you can call Kotlin/JS from Kotlin/JVM. Outbound parameters are encoded in the JVM and decoded
in JavaScript.
