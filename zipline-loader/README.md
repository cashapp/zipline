Zipline Loader
==============

This module provides structured, cached, loading over the network of JavaScript modules into
Zipline's QuickJS instance.

This module loads code from three places:

 - embedded: built-in resources (ie. packaged in a .jar file)
 - cache: disk cache
 - network

## Embedded

Files shipped with the app package use a flat file structure.

 - Manifest files should have the application name with the following file extension
   `.manifest.zipline.json` like `stockapp.manifest.zipline.json`.
 - Module files (ie. the JavaScript files pre-compiled to bytecode) are stored with their filename
   as the SHA-256 of their contents. All such files are in a single directory. This lets
   applications share common modules like the Kotlin standard library.

```
/embedded
  /zipline
    /searchapp.manifest.zipline.json
    /stockapp.manifest.zipline.json
    /123sdf234sdf234
    /234sd23dsf234sd
    /34897kj98987oik
    /890wer234sdsdfl
```

## Pinning

When an application's successfully loads, its manifest and modules are _pinned_. Pinned files are
not deleted when the cache is pruned. They are unpinned when a newer version is successfully loaded.
