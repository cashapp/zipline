Duktape Android
===============

The [Duktape embeddable JavaScript engine][duk] packaged for Android.


Usage
-----

```java
Duktape duktape = Duktape.create();
try {
  Log.d("Greeting", duktape.evaluate("'hello world'.toUpperCase();"));
} finally {
  duktape.close();
}
```

## Calling Java from JavaScript

You can bind a Java object to a JavaScript global, and call Java functions from JavaScript!
Currently, the following Java types are supported for function arguments and return values:

 * `boolean` and `Boolean`
 * `int` and `Integer`
 * `double` and `Double`
 * `String`

`void` return values are also supported.

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

Now you can bind the object to a JavaScript global, making it available in JavaScript code:

```java
Duktape duktape = Duktape.create();
try {
  // Bind our interface to a JavaScript object called Utf8.
  duktape.bind("Utf8", Utf8.class, utf8);

  String greeting = duktape.evaluate("" +
      // Here we have a hex encoded string.
      "var hexEnc = 'EC9588EB8595ED9598EC84B8EC9A9421';\n" +
      // Call out to Java to decode it!
      "var message = Utf8.fromHex(hexEnc);\n" +
      "message;");

  Log.d("Greeting", greeting);
} finally {
  duktape.close();
}
```

Download
--------

```groovy
compile 'com.squareup.duktape:duktape-android:0.9.5'
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

**NOTE:** When building with Android NDK r11b, the NDK bundle has an issue where the path to
clang is incorrect. This can be fixed by creating a symlink called `<ndk dir>/toolchains/llvm-3.8`
to `<ndk dir>/toolchains/llvm`.

## For Mac

```
./build_mac
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
