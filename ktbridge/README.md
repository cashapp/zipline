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

Create an interface and value types that'll be shared between platforms.

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


Create a Bridge in Kotlin/JS
----------------------------

Create an object in Kotlin/JS that you'd like to expose to Kotlin/JVM. Then call
`createBridgeToJs()` with that object and assign the return value to a global property.

```kotlin
package com.example

import app.cash.quickjs.ktbridge.createBridgeToJs

val echoService: EchoService = ...

@JsExport
val echoServiceBridge = createBridgeToJs(echoService, EchoJsAdapter)
```

Use the Bridge from Kotlin/JVM
------------------------------

Load your Kotlin/JS module into QuickJS. The bridge global property should be ready before you
attempt to access it from the JVM.

Call the `quickjs.getBridgeToJs()` extension function to get an instance.

```kotlin
val echoService = quickjs.getBridgeToJs<EchoService>(
  webpackModuleName = "testing",
  packageName = "com.example",
  propertyName = "echoServiceBridge",
  jsAdapter = EchoJsAdapter
)
```

Now you can call Kotlin/JS from Kotlin/JVM. Outbound parameters are encoded in the JVM and decoded
in JavaScript.
