package app.cash.zipline.loader

import kotlinx.datetime.Clock
import okio.ByteString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Zipline Cache State Machine
 *
 *            download
 *              start
 *         --------------->
 *   ABSENT                DIRTY
 *         <---------------
 *    ^       download       | download
 *    |        failed        | success
 *    |                      |
 *    |                      v
 *    '------------------ READY
 *           pruned
 *
 *
 * ABSENT entries are not present in sqlite.
 *
 * This cache is safe for concurrent use. If multiple callers attempt to mark the same ABSENT file
 * as DIRTY simultaneously, all but one will fail and return false.
 *
 *
 *
 *
 *
 *
 *
 *
 */
class ZiplineCache(
  private val fileSystem: FileSystem,
  private val directory: Path,
  private val database: Database,
  private val maxSizeInBytes: Int = 100 * 1024 * 1024, // 100mb
) {
  /**
   * Returns true if the file was absent and is now DIRTY. The caller is now the owner of this file
   * and may proceed to write the file to the file system.
   */
  fun setDirty(
    sha256: ByteString,
  ): Boolean = try {
    database.cacheQueries.insert(
      sha256_hex = sha256.hex(),
      file_state = FileState.DIRTY,
      size_bytes = 0L,
      last_used_at_epoch_ms = Clock.System.now().toEpochMilliseconds()
    )
    true
  } catch (e: SQLiteException) {
    // Presumably the file is already dirty.
    false
  }

  fun setReady(
    sha256: ByteString,
    fileSizeBytes: Long
  ): Boolean = try {
    if (getOrNull(sha256)?.file_state == FileState.DIRTY) {
      database.cacheQueries.update(
        sha256_hex = sha256.hex(),
        file_state = FileState.READY,
        size_bytes = fileSizeBytes,
        last_used_at_epoch_ms = Clock.System.now().toEpochMilliseconds()
      )
      true
    } else {
      // File is not present in DB in DIRTY state as expected.
      false
    }
  } catch (e: SQLiteException) {
    // Presumably the update failed.
    false
  }

  fun deleteDirty(
    sha256: ByteString
  ): Boolean = try {
    if (getOrNull(sha256)?.file_state == FileState.DIRTY) {
      database.cacheQueries.delete(
        sha256_hex = sha256.hex(),
      )
      true
    } else {
      // File is not present in DB in DIRTY state as expected.
      false
    }
  } catch (e: SQLiteException) {
    // Presumably the update failed.
    false
  }

  fun deleteReady(
    sha256: ByteString
  ): Boolean = try {
    if (getOrNull(sha256)?.file_state == FileState.READY) {
      database.cacheQueries.delete(
        sha256_hex = sha256.hex(),
      )
      true
    } else {
      // File is not present in DB in DIRTY state as expected.
      false
    }
  } catch (e: SQLiteException) {
    // Presumably the update failed.
    false
  }

  fun getOrNull(
    sha256: ByteString
  ): Files? = database.cacheQueries.get(sha256.hex()).executeAsOneOrNull()

  fun prune() {
    val currentSize = database.cacheQueries.selectCacheSumBytes().executeAsOne().SUM ?: 0L
    if (currentSize < maxSizeInBytes) return

    val files = database.cacheQueries.selectAll().executeAsList()

    val toDelete = files.toMutableList()
    var remainingQuota = maxSizeInBytes
    for (currentFile in files.sortedByDescending { it.last_used_at_epoch_ms }) {
      if ((remainingQuota - currentFile.size_bytes) < 0) break
      remainingQuota -= currentFile.size_bytes.toInt()
      toDelete.removeLast()
    }

    toDelete.forEach {
      fileSystem.delete(
        (directory.toString() + it.sha256_hex).toPath()
      )
      database.cacheQueries.delete(it.sha256_hex)
    }
  }
}

