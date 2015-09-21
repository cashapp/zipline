Duktape Android
===============

The [Duktape embeddable Javascript engine][duk] packaged for Android.


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


Download
--------

```groovy
compile 'com.squareup.duktape:duktape-android:0.9.1'
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
