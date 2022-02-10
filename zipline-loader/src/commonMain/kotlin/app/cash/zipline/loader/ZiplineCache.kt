package app.cash.zipline.loader

import com.squareup.sqldelight.db.Closeable
import com.squareup.sqldelight.db.SqlDriver
import okio.ByteString
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path

/**
 * Stores downloaded files. Files are named by their SHA-256 hashes. We use a SQLite database for file metadata: which
 * files are currently being downloaded, when they were most recently accessed, and what the total on-disk cost is.
 *
 * ### Cache Size
 *
 * The [maxSizeInBytes] limit applies to the content of the files in the `READY` state. It doesn't account for the size
 * of the metadata DB, or any in-flight downloads. Use a smaller [maxSizeInBytes] if you have a strict on-disk limit.
 *
 *
 * ### State Machine
 *
 * ```
 *            download
 *              start
 *         --------------->
 *  (absent)                 DIRTY
 *         <---------------
 *    ^       download       | download
 *    |        failed        | success
 *    |                      |
 *    |                      v
 *    '------------------ READY
 *           pruned
 * ```
 *
 * Note that absent entries are not present in SQLite.
 *
 * This cache is safe for concurrent use within a single process. If multiple callers attempt to mark the same absent
 * file as `DIRTY` simultaneously, all but one will fail and return false.
 *
 * This cache does not prevent multiple processes from accessing the cache simultaneously. Don't do this; it'll corrupt
 * the cache and behavior is undefined.
 */
class ZiplineCache internal constructor(
  private val driver: SqlDriver,
  private val fileSystem: FileSystem,
  private val directory: Path,
  private val maxSizeInBytes: Long,
  private val nowMs: () -> Long,
) : Closeable {
  private val database = createDatabase(driver)

  override fun close() {
    driver.close()
  }

  fun write(sha256: ByteString, content: ByteString) {
    if (!setDirty(sha256)) return
    fileSystem.createDirectories(directory)
    fileSystem.write(path(sha256)) {
      write(content)
    }
    setReady(sha256, content.size.toLong())
  }

  /**
   * Handle competing read and download.
   *
   * If file is downloading, reading thread will suspend until download is complete.
   *
   * Concurrent downloads on the same [sha256] are not ever done. One thread loses and suspends.
   */
  suspend fun getOrPut(sha256: ByteString, download: suspend () -> ByteString): ByteString {
    val read = read(sha256)
    if (read != null) return read

    val contents = download()
    write(sha256, contents)
    return contents
  }

  fun read(
    sha256: ByteString
  ): ByteString? {
    // TODO: consider preventing TERRIBLE RACES where the file is pruned and then made DIRTY all while we read.
    val metadata = database.cacheQueries.get(sha256.hex()).executeAsOneOrNull() ?: return null
    if (metadata.file_state != FileState.READY) return null
    return try {
      fileSystem.read(path(sha256)) {
        readByteString()
      }
    } catch (e: FileNotFoundException) {
      null // Might have been pruned while we were trying to read.
    }
  }

  /**
   * Returns true if the file was absent and is now `DIRTY`. The caller is now the exclusive owner of this file
   * and should proceed to write the file to the file system.
   */
  private fun setDirty(
    sha256: ByteString,
  ): Boolean = try {
    // Go from absent to DIRTY.
    database.cacheQueries.insert(
      sha256_hex = sha256.hex(),
      file_state = FileState.DIRTY,
      size_bytes = 0L,
      last_used_at_epoch_ms = nowMs()
    )
    true
  } catch (e: SQLiteException) {
    // Presumably the file is already dirty.
    false
  }

  /**
   * Returns true if the file was `DIRTY` and is now `READY`. The caller is no longer the exclusive owner of this file.
   * It may be deleted later to enforce the [maxSizeInBytes] constraint.
   *
   * This cache does not guarantee that the file will still exist after the call to [setReady]. In particular, a file
   * that exceeds the cache [maxSizeInBytes] will be deleted before this function returns. Load the file before calling
   * this method if that's problematic.
   */
  private fun setReady(
    sha256: ByteString,
    fileSizeBytes: Long
  ) {
    database.transaction {
      // Go from DIRTY to READY.
      require(getOrNull(sha256)?.file_state == FileState.DIRTY) {
        "file $sha256 was not DIRTY ... multiple processes sharing a cache?"
      }

      database.cacheQueries.update(
        sha256_hex = sha256.hex(),
        file_state = FileState.READY,
        size_bytes = fileSizeBytes,
        last_used_at_epoch_ms = nowMs()
      )
    }

    prune()
  }

  /** Deletes the file from the file system and the metadata DB. */
  private fun deleteDirty(
    sha256: ByteString
  ) {
    // Go from DIRTY to absent.
    fileSystem.delete(path(sha256))
    database.transaction {
      require(getOrNull(sha256)?.file_state == FileState.DIRTY) {
        "file $sha256 was not DIRTY ... multiple processes sharing a cache?"
      }
      database.cacheQueries.delete(sha256_hex = sha256.hex())
    }
  }

  private fun getOrNull(
    sha256: ByteString
  ): Files? = database.cacheQueries.get(sha256.hex()).executeAsOneOrNull()

  /**
   * Prune should be called on app boot to take into account any in-flight failed downloads or limit changes.
   *
   * Prune is also called when any file transitions from [DIRTY] to [READY] since that file is now included
   * in limit calculations.
   *
   * Note: if a single file is larger than [maxSizeInBytes], it will be deleted immediately upon calls to [setReady].
   * Callers must open such files for read before marking them as ready, assuming UNIX filesystem semantics where
   * open files are not deleted from under processes that have opened them.
   */
  internal fun prune() {
    // TODO rewrite with prune then recalculate after each file to prevent disk contention risk
    //  select * Ready files order by last used at limit 1
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
      fileSystem.delete(path(it.sha256_hex))
      // TODO enforce that this file is READY so we don't delete partial downloads
      database.cacheQueries.delete(it.sha256_hex)
    }
  }

  private fun path(sha256: ByteString): Path = directory / sha256.hex()

  private fun path(sha256: String): Path = directory / sha256
}

// TODO make this an expect with actual impl taking path instead of driver and per platform impl that sets up the driver
//   expect fun getDriver(path): Driver
expect fun openZiplineCache(
  fileSystem: FileSystem,
  dbPath: Path,
  directory: Path,
  maxSizeInBytes: Long = 100L * 1024L * 1024L, // 100 MiB.
): ZiplineCache
