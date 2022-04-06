package app.cash.zipline.loader.fetcher

import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Fetch from embedded fileSystem that ships with the app.
 */
class FsEmbeddedFetcher(
  private val embeddedDir: Path,
  private val embeddedFileSystem: FileSystem,
) : Fetcher {
  override suspend fun fetch(
    id: String,
    sha256: ByteString,
    url: String,
    fileNameOverride: String?
  ): ByteString? {
    val filePath = embeddedDir / (fileNameOverride ?: sha256.hex())
    return when {
      embeddedFileSystem.exists(filePath) -> {
        embeddedFileSystem.read(filePath) {
          readByteString()
        }
      }
      else -> {
        null
      }
    }
  }
}
