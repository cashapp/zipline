package app.cash.zipline.loader.interceptors.getter

import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.ZiplineHttpClient
import kotlinx.coroutines.sync.Semaphore
import okio.FileSystem
import okio.Path

/**
 * Factory helper methods to create list of [GetterInterceptor] to use in ZiplineLoader
 */
object GetterInterceptorFactory {
  fun createDownloadOnly(
    httpClient: ZiplineHttpClient,
    concurrentDownloadsSemaphore: Semaphore,
  ): List<GetterInterceptor> = listOf(
    HttpGetterInterceptor(
      httpClient = httpClient,
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
    )
  )

  fun createProduction(
    httpClient: ZiplineHttpClient,
    concurrentDownloadsSemaphore: Semaphore,
    embeddedFileSystem: FileSystem,
    embeddedDirectory: Path,
    cache: ZiplineCache,
  ): List<GetterInterceptor> = listOf(
    FsEmbeddedGetterInterceptor(
      embeddedFileSystem = embeddedFileSystem,
      embeddedDirectory = embeddedDirectory

    ),
    FsCacheGetterInterceptor(
      cache = cache,
    ),
    HttpPutInFsCacheGetterInterceptor(
      httpClient = httpClient,
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      cache = cache,
    )
  )
}
