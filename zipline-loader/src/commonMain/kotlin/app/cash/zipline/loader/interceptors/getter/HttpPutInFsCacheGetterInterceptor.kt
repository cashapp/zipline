package app.cash.zipline.loader.interceptors.getter

import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
import app.cash.zipline.loader.ZiplineHttpClient
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.ByteString

/**
 * Get the [ZiplineFile] from the network and save to local fileSystem cache once downloaded.
 */
class HttpPutInFsCacheGetterInterceptor(
  private val httpClient: ZiplineHttpClient,
  private val concurrentDownloadsSemaphore: Semaphore,
  private val cache: ZiplineCache,
): GetterInterceptor {
  override suspend fun get(id: String, sha256: ByteString, url: String): ZiplineFile? =
    cache.getOrPut(sha256) {
      concurrentDownloadsSemaphore.withPermit {
        httpClient.download(url)
      }
    }.toZiplineFile()
}
