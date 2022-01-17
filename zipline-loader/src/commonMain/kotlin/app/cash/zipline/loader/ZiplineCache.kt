package app.cash.zipline.loader

import kotlinx.datetime.Clock
import okio.ByteString

class ZiplineCache(
  private val database: Database,
  private val cacheMaxSizeInBytes: Int = 100 * 1024 * 1024, // 100mb
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
    database.cacheQueries.insert(
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
    if (currentSize < cacheMaxSizeInBytes) return

    val files = database.cacheQueries.selectAll().executeAsList()

    var remainingQuota = cacheMaxSizeInBytes
    val toDelete = mutableListOf<Files>()
    for (currentFile in files.sortedByDescending { it.last_used_at_epoch_ms }) {
      if ((remainingQuota - currentFile.size_bytes) < 0) break

      remainingQuota -= currentFile.size_bytes.toInt()
      toDelete.drop(1)
    }


  }
}
