# QuickJS Java

The [QuickJS embeddable JavaScript engine][qjs] packaged for Android and the JVM.

_(Looking for [Duktape Android](#Duktape)?)_


## Usage

```java
try (QuickJs engine = QuickJs.create()) {
  Log.d("Greeting", engine.evaluate("'hello world'.toUpperCase();").toString());
}
```

For long-lived usage feel free to call `QuickJs.create()` and retain the returned instance for as
long as you need. Be sure to call `.close()` when you are done to avoid leaking native components.

### Supported Java Types

Currently, the following Java types are supported when interfacing with JavaScript:

 * `boolean` and `Boolean`
 * `int` and `Integer` - as an argument only (not a return value) when calling JavaScript from Java.
 * `double` and `Double`
 * `String`
 * `void` - as a return value.

`Object` is also supported in declarations, but the type of the actual value passed must be
one of the above or `null`.

### Calling Java from JavaScript

You can provide a Java object for use as a JavaScript global, and call Java functions from
JavaScript!

#### Example

Suppose we wanted to expose the functionality of [Okio's `ByteString`][okio] in JavaScript to
convert hex-encoded strings back into UTF-8. First, define a Java interface that declares
the methods you would like to call from JavaScript:

```java
interface Utf8 {
  String fromHex(String hex);
}
```

Next, implement the interface in Java code (we leave the heavy lifting to Okio):

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
engine.set("Utf8", Utf8.class, utf8);

String greeting = (String) engine.evaluate(""
    // Here we have a hex encoded string.
    + "var hexEnc = 'EC9588EB8595ED9598EC84B8EC9A9421';\n"
    // Call out to Java to decode it!
    + "var message = Utf8.fromHex(hexEnc);\n"
    + "message;");

Log.d("Greeting", greeting);
```

### Calling JavaScript from Java

You can attach a Java interface to a JavaScript global object, and call JavaScript functions
directly from Java!  The same Java types are supported for function arguments and return
values as the opposite case above.

#### Example

TODO fix this example, as it no longer works with QuickJS!
You should still be able to understand what is going on, though.

Imagine a world where we don't have [Okio's `ByteString`][okio]. Fortunately, there's a [Duktape
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
engine.evaluate(""
    + "var Utf8 = {\n"
    + "  fromHex: function(v) { return String(Duktape.dec('hex', v)); }\n"
    + "};");
```

Now you can connect our interface to the JavaScript global, making it available in Java code:

```java
// Connect our interface to a JavaScript object called Utf8.
Utf8 utf8 = engine.get("Utf8", Utf8.class);

// Call into the JavaScript object to decode a string.
String greeting = utf8.fromHex("EC9588EB8595ED9598EC84B8EC9A9421");
Log.d("Greeting", greeting);
```

## Download

### Android

```groovy
repositories {
  mavenCentral()
}
dependencies {
  implementation 'app.cash.quickjs:quickjs-android:0.9.1'
}
```

This library is provided as a "fat" `.aar` with native binaries for all available architectures.
To reduce your binary size, use
[ABI filtering/splitting](https://developer.android.com/studio/build/configure-apk-splits.html)
when building an APK and/or enable
[Android App Bundles](https://developer.android.com/guide/app-bundle).

<details>
<summary>Snapshots of the development version are available in Sonatype's snapshots repository.</summary>
<p>

```groovy
repository {
  mavenCentral()
  maven {
    url 'https://oss.sonatype.org/content/repositories/snapshots/'
  }
}
dependencies {
  implementation 'app.cash.quickjs:quickjs-android:1.0.0-SNAPSHOT'
}
```

</p>
</details>


### JVM

```groovy
repositories {
  mavenCentral()
}
dependencies {
  implementation 'app.cash.quickjs:quickjs-jvm:0.9.1'
}
```

Only Linux and Mac OS are currently supported by the JVM artifact.

<details>
<summary>Snapshots of the development version are available in Sonatype's snapshots repository.</summary>
<p>

```groovy
repository {
  mavenCentral()
  maven {
    url 'https://oss.sonatype.org/content/repositories/snapshots/'
  }
}
dependencies {
  implementation 'app.cash.quickjs:quickjs-jvm:1.0.0-SNAPSHOT'
}
```

</p>
</details>


## Building

### For Android

```
$ ./gradlew :quickjs:android:build
```

The build system will automatically cross-compile the native library to all applicable ABIs and
create the fat `.aar` in `quickjs/android/build/outputs/aar/`.

This will not run the tests. You must have a working device or emulator connected at which point
you can explicitly run them.

```
$ ./gradlew :quickjs:android:connectedCheck
```

### For the JVM

First, build the native library for your host OS:
```
$ cmake -S quickjs/jvm/ -B build/jni/ -DQUICKJS_VERSION="$(cat quickjs/common/native/quickjs/VERSION)"
$ cmake --build build/jni/ --verbose
```

Next, copy the resulting binary into the resources of the JVM project:
```
$ mkdir -p quickjs/jvm/src/main/resources/
$ cp -v build/jni/libquickjs.* quickjs/jvm/src/main/resources/
```

Finally, build the platform-specific `.jar` and run the tests:
```
$ ./gradlew :quickjs:jvm:build
```

The `.jar` will be available in `quickjs/jvm/build/libs/`. 


## License

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


Note: The included C code from QuickJS is licensed under MIT.


## Duktape

This repository used to host an Android-specific packaging of the [Duktape](https://duktape.org/)
engine. We have changed to using QuickJS with exactly the same features and API. The Duktape
history is still present in this repo as are the release tags. Available versions are listed on
[Maven central](https://search.maven.org/artifact/com.squareup.duktape/duktape-android).




 [qjs]: https://bellard.org/quickjs/
 [okio]: https://github.com/square/okio/blob/master/okio/src/main/java/okio/ByteString.java
