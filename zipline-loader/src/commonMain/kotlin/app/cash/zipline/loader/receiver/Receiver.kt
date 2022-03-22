package app.cash.zipline.loader.receiver

import okio.ByteString

/**
 * After getting a [ByteString], [Receiver]s are iteratively called in order to handle.
 */
interface Receiver {
  /**
   * Handle a [ByteString] which can include loading it into a [Zipline] runtime
   *  or saving to fileSystem.
   */
  suspend fun receive(
    byteString: ByteString,
    id: String,
    sha256: ByteString
  )
}

