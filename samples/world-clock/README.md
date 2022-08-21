World Clock
===========

This mobile app sample has these modules:

 * **presenters** is a Kotlin/Multiplatform library that shows the time in various time zones.
 * **android** and **iOS** apps continuously fetch the latest presenter and run it.

Prerequisites
-------------

In order to build and run these applications you'll need to:

 * Have Android Studio installed
 * Have `cmake` installed (on Mac, you can use `brew install cmake`)
 * Build QuickJS for your platform using `./.github/workflows/build-mac.sh` (replace `-mac` with the
   appropriate script for your machine type). This is a one-time process.


Serving Presenters
------------------

Run this:

```
./gradlew -p samples world-clock:presenters:serveDevelopmentZipline --info --continuous
```

This will compile Kotlin/JS and serve it at [[http://localhost:8080/manifest.zipline.json]]. The
server will run until you CTRL+C the process.


Android
-------

Run this:

```
./gradlew -p samples world-clock:android:installDebug
```

This Android app assumes it's running in an emulator and will attempt to fetch code from the
devserver running on the host machine (10.0.2.2).


iOS
---

Run this:

```
cd samples/world-clock/ios/app
pod install
open WorldClock.xcworkspace
```

Then build and run the app. The shared Kotlin code will be built automatically as part of building
the iOS app, and also rebuilt as needed.

The app pulls the Kotlin/JS from the presenters server and requires it to be running in order to
work.


Live Edits
----------

Make changes to `RealWorldClockPresenter` - this will trigger a recompilation of the Zipline code.
When it's ready, relaunch the Android application.

