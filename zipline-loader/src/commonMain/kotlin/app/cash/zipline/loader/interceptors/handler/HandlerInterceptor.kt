package app.cash.zipline.loader.interceptors.handler

import app.cash.zipline.loader.ZiplineFile
import okio.ByteString

/**
 * After getting a [ZiplineFile], handler interceptors are iteratively called in order to handle.
 */
interface HandlerInterceptor {
  /**
   * Handle a [ZiplineFile] which can include loading it into a [Zipline] runtime
   *  or saving to fileSystem.
   */
  suspend fun handle(
    ziplineFile: ZiplineFile,
    id: String,
    sha256: ByteString
  )
}

