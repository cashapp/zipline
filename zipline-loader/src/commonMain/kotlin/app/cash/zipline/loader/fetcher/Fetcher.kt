package app.cash.zipline.loader.fetcher

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.ByteString

/**
 * A list of [Fetcher] delegate in order responsibility of getting the desired [ByteString].
 *
 * If an interceptor does not get a file, it returns null and the next [Fetcher] is called.
 */
interface Fetcher {
  /**
   * Get the desired [ByteString] or null if not found.
   */
  suspend fun fetch(
    id: String,
    sha256: ByteString,
    url: String,
    fileNameOverride: String? = null
  ): ByteString?
}

/**
 * Use a [concurrentDownloadsSemaphore] to control parallelism of fetching operations.
 */
suspend fun List<Fetcher>.fetch(
  concurrentDownloadsSemaphore: Semaphore,
  id: String,
  sha256: ByteString,
  url: String,
  fileNameOverride: String? = null,
): ByteString = concurrentDownloadsSemaphore
  .withPermit {
    var byteString: ByteString? = null
    for (fetcher in this) {
      byteString = fetcher.fetch(id, sha256, url, fileNameOverride)
      if (byteString != null) break
    }

    checkNotNull(byteString) {
      "Unable to get ByteString for [id=$id][sha256=$sha256][url=$url]"
    }
    return byteString
  }
