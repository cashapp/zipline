package app.cash.zipline.loader.fetcher

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
    url: String
  ): ByteString?
}
