package app.cash.zipline.loader.receiver

import app.cash.zipline.Zipline
import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
import app.cash.zipline.loader.multiplatformLoadJsModule
import okio.ByteString

/**
 * Load the [ZiplineFile] into a Zipline runtime instance.
 */
class ZiplineLoadReceiver(
  private val zipline: Zipline
) : Receiver {
  override suspend fun receive(byteString: ByteString, id: String, sha256: ByteString) {
    zipline.multiplatformLoadJsModule(byteString.toZiplineFile().quickjsBytecode.toByteArray(), id)
  }
}
