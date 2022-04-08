package app.cash.zipline.loader.fetcher

import app.cash.zipline.loader.ZiplineCache
import okio.ByteString

/**
 * Fetch from the network and save to local fileSystem cache once downloaded.
 */
class FsCachingFetcher(
  private val cache: ZiplineCache,
  private val delegate: Fetcher,
) : Fetcher {
  override suspend fun fetch(
    id: String,
    sha256: ByteString,
    url: String,
    fileNameOverride: String?
  ): ByteString? =
    cache.getOrPut(sha256) {
      delegate.fetch(id, sha256, url, fileNameOverride)!!
    }
}
