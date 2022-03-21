package app.cash.zipline.loader.interceptors.getter

import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
import okio.ByteString

class FsCacheGetterInterceptor(
  private val cache: ZiplineCache,
  ): GetterInterceptor {
  override suspend fun get(id: String, sha256: ByteString, url: String): ZiplineFile? =
    cache.read(sha256)?.toZiplineFile()
}
