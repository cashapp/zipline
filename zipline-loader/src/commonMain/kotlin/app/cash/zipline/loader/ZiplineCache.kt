package app.cash.zipline.loader

import kotlinx.datetime.Clock
import okio.ByteString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class ZiplineCache(
  private val fileSystem: FileSystem,
  private val directory: Path,
  private val database: Database,
  private val maxSizeInBytes: Int = 100 * 1024 * 1024, // 100mb
) {
  fun setDirty(
    sha256: ByteString,
  ) {
    database.cacheQueries.insert(
      sha256_hex = sha256.hex(),
      file_state = FileState.DIRTY,
      size_bytes = 0L,
      last_used_at_epoch_ms = Clock.System.now().toEpochMilliseconds()
    )
  }

  fun setReady(
    sha256: ByteString,
    fileSizeBytes: Long
  ) {
    database.cacheQueries.update(
      sha256_hex = sha256.hex(),
      file_state = FileState.READY,
      size_bytes = fileSizeBytes,
      last_used_at_epoch_ms = Clock.System.now().toEpochMilliseconds()
    )
  }

  fun getOrNull(
    sha256: ByteString
  ) = database.cacheQueries.get(sha256.hex()).executeAsOneOrNull()

  fun prune() {
    val currentSize = database.cacheQueries.selectCacheSumBytes().executeAsOne().SUM ?: 0L
    if (currentSize < maxSizeInBytes) return

    val files = database.cacheQueries.selectAll().executeAsList()

    val toDelete = mutableListOf<Files>()
    var remainingQuota = maxSizeInBytes
    for (currentFile in files.sortedByDescending { it.last_used_at_epoch_ms }) {
      if ((remainingQuota - currentFile.size_bytes) < 0) break
      remainingQuota -= currentFile.size_bytes.toInt()
      toDelete.drop(1)
    }

    toDelete.forEach {
      fileSystem.delete(
        (directory.toString() + it.sha256_hex).toPath()
      )
      database.cacheQueries.delete(it.sha256_hex)
    }
  }
}
