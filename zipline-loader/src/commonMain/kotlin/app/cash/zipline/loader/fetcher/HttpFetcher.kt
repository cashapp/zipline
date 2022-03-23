package app.cash.zipline.loader.fetcher

import app.cash.zipline.loader.ZiplineHttpClient
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.ByteString

/**
 * Fetch from the network.
 */
class HttpFetcher(
  private val httpClient: ZiplineHttpClient,
  private val concurrentDownloadsSemaphore: Semaphore,
) : Fetcher {
  override suspend fun fetch(id: String, sha256: ByteString, url: String): ByteString? =
    concurrentDownloadsSemaphore.withPermit {
      httpClient.download(url)
    }
}
