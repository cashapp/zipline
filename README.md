Duktape Android
===============

The [Duktape embeddable JavaScript engine][duk] packaged for Android.

Usage
-----

```java
Duktape duktape = Duktape.create();
try {
  Log.d("Greeting", duktape.evaluate("'hello world'.toUpperCase();").toString());
} finally {
  duktape.close();
}
```

## Supported Java Types

Currently, the following Java types are supported when interfacing with JavaScript:

 * `boolean` and `Boolean`
 * `int` and `Integer` - as an argument only (not a return value) when calling JavaScript from Java.
 * `double` and `Double`
 * `String`
 * `void` - as a return value.

`Object` is also supported in declarations, but the type of the actual value passed must be
one of the above or `null`.

## Calling Java from JavaScript

You can provide a Java object for use as a JavaScript global, and call Java functions from
JavaScript!  

### Example

Suppose we wanted to expose the functionality of [okio's ByteString][okio] in JavaScript to
convert hex-encoded strings back into UTF-8. First, define a Java interface that declares
the methods you would like to call from JavaScript:

```java
interface Utf8 {
  String fromHex(String hex);
}
```

Next, implement the interface in Java code (we leave the heavy lifting to okio):

```java
Utf8 utf8 = new Utf8() {
  @Override public String fromHex(String hex) {
    return okio.ByteString.decodeHex(hex).utf8();
  }
};
```

Now you can set the object to a JavaScript global, making it available in JavaScript code:

```java
// Attach our interface to a JavaScript object called Utf8.
duktape.set("Utf8", Utf8.class, utf8);

String greeting = (String) duktape.evaluate(""
    // Here we have a hex encoded string.
    + "var hexEnc = 'EC9588EB8595ED9598EC84B8EC9A9421';\n"
    // Call out to Java to decode it!
    + "var message = Utf8.fromHex(hexEnc);\n"
    + "message;");

Log.d("Greeting", greeting);
```

## Calling JavaScript from Java

You can attach a Java interface to a JavaScript global object, and call JavaScript functions
directly from Java!  The same Java types are supported for function arguments and return
values as the opposite case above.

### Example

Imagine a world where we don't have [okio's ByteString][okio]. Fortunately, there's a [Duktape
builtin][dukdec] that allows us to convert hex-encoded strings back into UTF-8! We can easily set up a
proxy that allows us to use it directly from our Java code. First, define a Java interface
that declares the JavaScript methods you would like to call:

```java
interface Utf8 {
  String fromHex(String hex);
}
```

Next, we define a global JavaScript object in Duktape to connect to:

```java
// Note that Duktape.dec returns a Buffer, we must convert it to a String return value.
duktape.evaluate(""
    + "var Utf8 = {\n"
    + "  fromHex: function(v) { return String(Duktape.dec('hex', v)); }\n"
    + "};");
```

Now you can connect our interface to the JavaScript global, making it available in Java code:

```java
// Connect our interface to a JavaScript object called Utf8.
Utf8 utf8 = duktape.get("Utf8", Utf8.class);

// Call into the JavaScript object to decode a string.
String greeting = utf8.fromHex("EC9588EB8595ED9598EC84B8EC9A9421");
Log.d("Greeting", greeting);
```

Download
--------

```groovy
compile 'com.squareup.duktape:duktape-android:1.2.0'
```

This library is provided as a "fat" aar with native binaries for all available architectures. To
reduce your APK size, use the ABI filtering/splitting techniques in the Android plugin:
http://tools.android.com/tech-docs/new-build-system/user-guide/apk-splits

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].


Building
--------

## For Android

```
./gradlew build
```

Set the `java.library.path` system property to `build/` when you execute Java.


License
-------

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


Note: The included C code from Duktape is licensed under MIT.



 [duk]: http://duktape.org/
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/
 [okio]: https://github.com/square/okio/blob/master/okio/src/main/java/okio/ByteString.java
 [dukdec]: http://duktape.org/guide.html#builtin-duktape-dec
