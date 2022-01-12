Zipline Loader
================

This module provides structured, cached, loading over the network of Javascript modules into Zipline's QuickJS instance.


Loading
-------

We look in three places:
 - built-in resources (ie. packaged in a .jar file)
 - cache
 - network

If a file is not built-in, and not in the cache, we:
 - create it as DIRTY in the cache DB and set its last_used_at date to now
 - download
 - if download completes successfully, mark it as READY in the cache DB
 - otherwise delete that row from the cache DB


class DownloadCache(
  val fileSystem: FileSystem,
  val cacheDirectory: Path,
  val cacheMaxSizeInBytes: Int = 100 * 1024 * 1024, // 100mb
) {

  suspend fun gimmeFile(
    sha256: ByteString,
    downloader: () -> ByteString,
  ) : BufferedSource {
    // Create a Flow that continuously queries this row
    // skip the ones where the row is DIRTY
    // that way we end up in a useful state where we don't need a while loop


    val query = selectStateOf(sha256)
      .skipAll { row.state == DIRTY }
      .first()


    while (true) {
      // check the file state
      if (file state == READY) {
        return fileSystem.read()
      }

      if (file state == null) {
        create as DIRTY in the database
        try {
          downloader()
          set file state to READY
          return fileSystem.read()
        } catch (e: Exception) {
          delete the row from the database
          throw e
        }
      }

      if (file state == DIRTY) {
        // There's nothing we can do at the moment. Just wait until that changes.
        query.waitUntilChanged()
        continue
      }
    }
  }
}

/** Stall out until this query's listener publishes something new. */
suspend fun Query.waitUntilChanged() {
  suspendCoroutine { continuation ->
    val listener = {
      query.removeListener(listener)
      continuation.resume(Unit)
    }
    query.addListener(listener)
  }
}


Pruning
-------

When the loader is created we immediately prune all DIRTY files (if they exist).
We also prune files ordered by least-recently used at until the total cache size is within
our MAX_CACHE_SIZE limit.




