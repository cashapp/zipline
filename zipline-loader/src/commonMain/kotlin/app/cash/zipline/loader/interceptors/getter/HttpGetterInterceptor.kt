package app.cash.zipline.loader.interceptors.getter

import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
import app.cash.zipline.loader.ZiplineHttpClient
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.ByteString

/**
 * Get the [ZiplineFile] from the network.
 */
class HttpGetterInterceptor(
  private val httpClient: ZiplineHttpClient,
  private val concurrentDownloadsSemaphore: Semaphore,
) : GetterInterceptor {
  override suspend fun get(id: String, sha256: ByteString, url: String): ZiplineFile? =
    concurrentDownloadsSemaphore.withPermit {
      httpClient.download(url)
    }.toZiplineFile()
}
