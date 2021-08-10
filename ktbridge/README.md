KtBridge
========

This library makes it easy call between Kotlin/JVM and Kotlin/JavaScript. It expands beyond the
built-in QuickJs binding to support custom types.

Define a Shared API
-------------------

Create an interface and value types that'll be shared between platforms.

```kotlin
interface EchoService {
  @JsName("echo")
  fun echo(request: EchoRequest): EchoResponse
}

data class EchoRequest(
  val message: String
)

data class EchoResponse(
  val message: String
)
```

You'll need `@JsName` on all function names.


Define a JsAdapter
------------------

Create a shared `JsAdapter` to encode and decode your parameter types.


Create a Bridge in Kotlin/JS
----------------------------

Create an object in Kotlin/JS that you'd like to expose to Kotlin/JVM. Then call
`createBridgeToJs()` with that object and assign the return value to a global property.

```kotlin
val echoService: EchoService = ...

@JsName("echoServiceBridge")
val echoServiceBridge = createBridgeToJs(echoService, EchoJsAdapter)
```

In your Kotlin/JS `build.gradle` make sure that global property is retained. The structure of a
`keep` argument combines the following:

 * The root project name (`rootProject.name` in `settings.gradle`, falling back to root project's
   directory name)
 * The Kotlin/JS project name (a subproject's directory name) 
 * The enclosing package name
 * The bridge property name

These are joined using a dash for the first delimiter and dots for subsequent delimiters. Here's a
sample:

```groovy
tasks {
  processDceJsKotlinJs {
    keep("rootprojectname-projectname.com.example.echoServiceBridge")
  }
}
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
