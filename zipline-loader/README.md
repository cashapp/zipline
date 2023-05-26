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

## Database

This module uses [SqlDelight](https://cashapp.github.io/sqldelight/) to interact with SQLite. The
database tracks downloaded file state, eviction, and pinning.

## Manifest

The Zipline Loader uses a manifest JSON file to enumerate all Zipline files, code signatures, and other metadata required to load all code for an application into a Zipline instance and run the application.

- `unsigned`: Each Manifest is signed to provide trusted code from build to client. Some manifest fields have a lower security requirement and not signing them makes hermetic, incremental builds, and caching easier so are nested in the `unsigned` keyspace to not be included in manifest signatures.
  - `signatures`: Map of signing key names to the hex-encoded signatures.
  - `freshAtEpochMs`: In some cases like embedding a manifest in an app release, we want to encode a timestamp to signal how fresh the code is to be used later in loading logic and prefer the newest code be loaded.
  - `baseUrl`: All URLs in the manifest are relative to the baseUrl if the field is set, otherwise they are relative to the URL that the manifest was downloaded from.
- `modules`: A map of module ID to Module which captures all of the modular code to load for the application.
  - `Module.url`: A relative URL for where to load the code, this URL can be used in loading from the network or local file system.
  - `Module.sha256`: sha256 hash of the contents of the module code, used to ensure integrity of the fetched code and as cache key.
  - `Module.dependsOnIds`: A list of module IDs which the module depends on and which must be loaded into the host Zipline prior to this module.
- `mainModuleId`: The module ID that is the entrypoint for the application. When topologically sorted based on dependencies, the `mainModuleId` will typically be the last module in the sorted list. It is used to identify the package where the `mainFunction` entrypoint for the application is located.
- `mainFunction`: The entrypoint function for the application which is in the package identified by the `mainModuleId`. This function, if set in the manifest, is called to launch your application and must include a call to `Zipline.get()` to complete startup tasks. For now, this needs to be set explicitly in your ZiplinePlugin Gradle or ZiplineDownloader config.
- `version`: An optional identifier for the version of the code identified in the manifest. For application code stored in a Git repo, the Git SHA would be a fitting version. The version can be helpful for debugging purposes.
