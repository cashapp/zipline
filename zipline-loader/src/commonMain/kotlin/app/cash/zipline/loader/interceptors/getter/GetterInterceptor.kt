package app.cash.zipline.loader.interceptors.getter

import app.cash.zipline.loader.ZiplineFile
import okio.ByteString

/**
 * A list of [GetterInterceptor] delegate in order responsibility of getting the desired [ZiplineFile].
 *
 * If an interceptor does not get a file, it returns null and the next interceptor is called.
 */
interface GetterInterceptor {
  /**
   * Get the module's [ZiplineFile] or null if not found.
   */
  suspend fun get(
    id: String,
    sha256: ByteString,
    url: String
  ): ZiplineFile?
}
