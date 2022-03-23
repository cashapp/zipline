package app.cash.zipline.loader.receiver

import app.cash.zipline.loader.ZiplineFile
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Save [ZiplineFile] to fileSystem.
 */
class FsSaveReceiver(
  val downloadFileSystem: FileSystem,
  val downloadDir: Path,
): Receiver {
  override suspend fun receive(byteString: ByteString, id: String, sha256: ByteString) {
    downloadFileSystem.createDirectories(downloadDir)
    downloadFileSystem.write(downloadDir / sha256.hex()) {
      write(byteString)
    }
  }
}
