package app.cash.zipline.loader.interceptors.handler

import app.cash.zipline.loader.ZiplineFile
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Save [ZiplineFile] to fileSystem.
 */
class FsSaveHandlerInterceptor(
  val downloadFileSystem: FileSystem,
  val downloadDir: Path,
): HandlerInterceptor {
  override suspend fun handle(
    ziplineFile: ZiplineFile,
    id: String,
    sha256: ByteString
  ) {
    downloadFileSystem.createDirectories(downloadDir)
    downloadFileSystem.write(downloadDir / sha256.hex()) {
      write(ziplineFile.toByteString())
    }
  }
}
