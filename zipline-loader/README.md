Zipline Loader
================

This module provides structured, cached, loading over the network of Javascript modules into Zipline's QuickJS instance.

This module loads code from three places:
 - embedded: built-in resources (ie. packaged in a .jar file)
 - cache: disk cache
 - network

## Embedded

Files shipped with the app package should adhere to the following flat file structure.

- Manifest files should have the application name with the following file extension `.manifest.zipline.json`, for example `stockapp.manifest.zipline.json`.
- Module files (ie. the Javascript files pre-compiled to bytecode as ZiplineFiles) are stored with their filename as the sha256 of the ZiplineFile. All ZiplineFiles used in different manifests are stored flat to reduce duplication across multiple apps which may share many modules like Kotlin Std Lib for example.

```
/embedded
  /zipline
    \
      - searchapp.manifest.zipline.json
      - stockapp.manifest.zipline.json
      - 123sdf234sdf234
      - 234sd23dsf234sd
      - 34897kj98987oik
      - 890wer234sdsdfl
```

## Pinning

When a manifest successfully loads, it is pinned as stable.

The manifest file and any constituent ZiplineFiles are pinned which prevents the files from being pruned.

The pinned manifest is not unpinned until a later successful load of a different manifest.

There is only ever 1 manifest pinned at a time. New manifest pins atomically remove the prior pinned manifest.
