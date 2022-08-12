Emoji Search
============

This is a mobile app demo of Zipline. It has two modules:

 * **presenters** is a Kotlin/Multiplatform library that searches a set of emoji images.
 * **android** is an Android application that downloads the presenters JavaScript and displays
   it.


Serving presenters.js
---------------------

Run this:

```
./gradlew -p samples emoji-search:presenters:serveDevelopmentZipline --info --continuous
```

This will compile Kotlin/JS and serve it at [[http://localhost:8080/presenters.js]]. The server will
run until you CTRL+C the process.


Running Emoji-Search
--------------------

Run this:

```
./gradlew -p samples emoji-search:android:installDebug
```

This Android app assumes it's running in an emulator and will attempt to fetch JavaScript from the
devserver running on the host machine (10.0.2.2). It will crash if that server is not reachable.


Live Edits
----------

Make changes to `RealEmojiSearchPresenter` - this will trigger a recompilation of the Zipline code.
When it's ready, relaunch the Android application.

