package app.cash.zipline.loader.interceptors.handler

import app.cash.zipline.Zipline
import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.multiplatformLoadJsModule
import okio.ByteString

/**
 * Load the [ZiplineFile] into a Zipline runtime instance.
 */
class ZiplineLoadHandlerInterceptor(
  private val zipline: Zipline,
  ): HandlerInterceptor {
  override suspend fun handle(
    ziplineFile: ZiplineFile,
    id: String,
    sha256: ByteString
  ) {
    zipline.multiplatformLoadJsModule(ziplineFile.quickjsBytecode.toByteArray(), id)
  }
}
