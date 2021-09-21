Emoji Search
============

This is an Android demo of Zipline. It has two modules:

 * **presenters** is a Kotlin/Multiplatform library that searches a set of emoji images.
 * **emoji-search** is an Android application that downloads the presenters JavaScript and displays
   it.


Serving presenters.js
---------------------

**NOTE:** There's a bug in Kotlin 1.5.21 that presents the devserver from launching. Manually edit
the Zipline root `build.gradle.kts` to use Kotlin 1.5.31 before building this. You will need to
change it back to 1.5.21 before installing the Android app.

Run this:

```
./gradlew samples:emoji-search:presenters:jsBrowserProductionRun --info
```

This will compile Kotlin/JS and serve it at [[http://localhost:8080/presenters.js]]. The server will
run until you CTRL+C the process.


Running Emoji-Search
--------------------

Run this:

```
./gradlew :samples:emoji-search:installDebug
```

This Android app assumes it's running in an emulator and will attempt to fetch JavaScript from the
devserver running on the host machine (10.0.2.2). It will crash if that server is not reachable.


Live Edits
----------

Make changes to `RealEmojiSearchPresenter`, then CTRL+C the devserver and start it again. When it's
ready, relaunch the Android application.

