/*
 * Copyright (C) 2022 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.loader

import app.cash.sqldelight.db.SqlDriver
import app.cash.zipline.loader.internal.cache.Database
import app.cash.zipline.loader.internal.cache.FileState
import app.cash.zipline.loader.internal.cache.Files
import app.cash.zipline.loader.internal.cache.SqlDriverFactory
import app.cash.zipline.loader.internal.cache.createDatabase
import app.cash.zipline.loader.internal.fetcher.LoadedManifest
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.Closeable
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path

/**
 * Stores downloaded files.
 *
 * The `maxSizeInBytes` limit applies to the content of all files. It doesn't account for the size
 * of the metadata database, or any in-flight downloads. Use a smaller value if you have a strict
 * on-disk limit.
 *
 * This cache is safe for concurrent use within a single process. This cache does not prevent
 * multiple processes from accessing the cache files simultaneously. Don't do this; it'll corrupt
 * the cache and behavior is undefined.
 *
 * If multiple threads in a single process operate on a cache instance simultaneously, downloads may
 * be repeated but no thread will be blocked.
 */
class ZiplineCache private constructor(
  private val driver: SqlDriver,
  private val database: Database,
  private val fileSystem: FileSystem,
  private val directory: Path,
  private val maxSizeInBytes: Long,
  private val loaderEventListener: LoaderEventListener,
  private var hasWriteFailures: Boolean,
) : Closeable {

  /*
   * Files are named by their SHA-256 hashes. We use a SQLite database for file metadata: which
   * files are currently being downloaded, when they were most recently accessed, and what the total
   * on-disk usage is.
   *
   * State Machine
   * -------------
   *
   *            download
   *              start
   *         --------------->
   *  (absent)                 DIRTY
   *         <---------------
   *    ^                      |
   *    |       download       | download
   *    |        failed        | success
   *    |                      v
   *    '------------------ READY
   *           pruned
   *
   * Note that absent entries are not present in SQLite.
   */

  override fun close() {
    driver.close()
  }

  private fun write(
    applicationName: String,
    sha256: ByteString,
    content: ByteString,
    nowEpochMs: Long,
    isManifest: Boolean = false,
    manifestFreshAtMs: Long? = null,
  ): Files {
    val metadata = openForWrite(
      applicationName = applicationName,
      sha256 = sha256,
      nowEpochMs = nowEpochMs,
      isManifest = isManifest,
      manifestFreshAtMs = manifestFreshAtMs,
    )
    write(metadata, content, nowEpochMs)
    return metadata
  }

  private fun write(metadata: Files, content: ByteString, nowEpochMs: Long) {
    fileSystem.write(path(metadata)) {
      write(content)
    }
    setReady(metadata, content.size.toLong(), nowEpochMs)
  }

  /**
   * Handle competing read and download.
   *
   * If file is downloading, reading thread will suspend until download is complete.
   *
   * Concurrent downloads on the same [sha256] are not ever done. One thread loses and suspends.
   */
  internal suspend fun getOrPut(
    applicationName: String,
    sha256: ByteString,
    nowEpochMs: Long,
    download: suspend () -> ByteString,
  ): ByteString {
    if (hasWriteFailures) return download()

    try {
      val read = read(sha256, nowEpochMs)
      if (read != null) return read
    } catch (e: Exception) {
      hasWriteFailures = true // Mark this cache as broken.
      loaderEventListener.cacheStorageFailed(applicationName, e)
      return download()
    }

    val content = download()
    try {
      write(
        applicationName = applicationName,
        sha256 = sha256,
        content = content,
        isManifest = false,
        nowEpochMs = nowEpochMs,
      )
    } catch (e: Exception) {
      hasWriteFailures = true // Mark this cache as broken.
      loaderEventListener.cacheStorageFailed(applicationName, e)
    }

    return content
  }

  private fun getOrPutManifest(
    applicationName: String,
    content: ByteString,
    putFreshAtMs: Long,
    nowEpochMs: Long,
  ): Files {
    val sha256 = content.sha256()
    val metadata = getOrNull(sha256)
    return metadata ?: write(
      applicationName = applicationName,
      sha256 = sha256,
      content = content,
      isManifest = true,
      manifestFreshAtMs = putFreshAtMs,
      nowEpochMs = nowEpochMs,
    )
  }

  internal fun read(
    sha256: ByteString,
    nowEpochMs: Long,
  ): ByteString? {
    val metadata = database.filesQueries.get(sha256.hex()).executeAsOneOrNull() ?: return null
    return read(metadata, nowEpochMs)
  }

  private fun read(
    metadata: Files,
    nowEpochMs: Long,
  ): ByteString? {
    if (metadata.file_state != FileState.READY) return null

    // Update the used at timestamp.
    database.filesQueries.update(
      id = metadata.id,
      file_state = metadata.file_state,
      size_bytes = metadata.size_bytes,
      last_used_at_epoch_ms = nowEpochMs,
    )
    val path = path(metadata)
    val result = try {
      fileSystem.read(path) {
        readByteString()
      }
    } catch (_: FileNotFoundException) {
      null // Might have been pruned while we were trying to read?
    }

    if (result == null || result.sha256() != metadata.sha256_hex.decodeHex()) {
      // File is absent or corrupt. Delete quietly.
      try {
        fileSystem.delete(path)
        database.filesQueries.delete(metadata.id)
      } catch (_: Exception) {
      }
      return null
    }

    return result
  }

  internal fun unpin(applicationName: String, sha256: ByteString) {
    val fileId = database.filesQueries.get(sha256.hex()).executeAsOneOrNull()?.id ?: return
    database.pinsQueries.delete_pin(applicationName, fileId)
  }

  /** Returns null if there is no pinned manifest. */
  internal fun getPinnedManifest(applicationName: String, nowEpochMs: Long): LoadedManifest? {
    if (hasWriteFailures) return null // This cache is broken.

    try {
      val manifestFile = database.filesQueries
        .selectPinnedManifest(applicationName)
        .executeAsOneOrNull() ?: return null
      val manifestBytes = read(manifestFile, nowEpochMs) ?: return null
      return LoadedManifest(manifestBytes, manifestFile.fresh_at_epoch_ms!!)
    } catch (e: Exception) {
      hasWriteFailures = true // Mark this cache as broken.
      loaderEventListener.cacheStorageFailed(applicationName, e)
      return null
    }
  }

  /** Pins manifest and unpins all other files and manifests */
  internal fun pinManifest(
    applicationName: String,
    loadedManifest: LoadedManifest,
    nowEpochMs: Long,
  ) {
    if (hasWriteFailures) return // This cache is broken.

    try {
      val manifestBytes = loadedManifest.manifestBytes
      val manifestMetadata = getOrPutManifest(
        applicationName = applicationName,
        content = manifestBytes,
        putFreshAtMs = loadedManifest.freshAtEpochMs,
        nowEpochMs = nowEpochMs,
      )

      database.transaction {
        database.pinsQueries.delete_application_pins(applicationName)

        // Pin all modules in this manifest.
        loadedManifest.manifest.modules.forEach { (_, module) ->
          database.filesQueries.get(module.sha256.hex()).executeAsOneOrNull()?.let { metadata ->
            createPinIfNotExists(applicationName, metadata.id)
          }
        }

        // Pin the manifest.
        createPinIfNotExists(applicationName, manifestMetadata.id)
      }
    } catch (e: Exception) {
      hasWriteFailures = true // Mark this cache as broken.
      loaderEventListener.cacheStorageFailed(applicationName, e)
    }
  }

  /**
   * Unpin manifest and make all files open to pruning, except those included
   * in another pinned manifest.
   */
  internal fun unpinManifest(
    applicationName: String,
    loadedManifest: LoadedManifest,
    nowEpochMs: Long,
  ) {
    if (hasWriteFailures) return // This cache is broken.

    try {
      val unpinManifestBytes = loadedManifest.manifestBytes
      val unpinManifestFile = database.filesQueries
        .get(unpinManifestBytes.sha256().hex())
        .executeAsOneOrNull()

      // Get fallback manifest metadata.
      val fallbackManifestFile: Files? = unpinManifestFile?.let {
        database.filesQueries
          .selectPinnedManifestNotFileId(applicationName, it.id)
          .executeAsOneOrNull()
      } ?: database.filesQueries
        .selectPinnedManifest(applicationName)
        .executeAsOneOrNull()

      // There is no fallback manifest, delete all pins and return.
      if (fallbackManifestFile == null) {
        database.pinsQueries.delete_application_pins(applicationName)
        return
      }

      // Pin the fallback manifest, which removes all pins prior to pinning.
      val fallbackManifestBytes = read(fallbackManifestFile, nowEpochMs)
        ?: throw FileNotFoundException(
          "No manifest file on disk with [fileName=${fallbackManifestFile.sha256_hex}]",
        )
      val fallbackManifest = LoadedManifest(
        fallbackManifestBytes,
        fallbackManifestFile.fresh_at_epoch_ms!!,
      )
      pinManifest(applicationName, fallbackManifest, nowEpochMs)
    } catch (e: Exception) {
      hasWriteFailures = true // Mark this cache as broken.
      loaderEventListener.cacheStorageFailed(applicationName, e)
    }
  }

  /**
   * Returns file metadata if the file was absent and is now `DIRTY`. The caller is now the
   * exclusive owner of this file and should proceed to write the file to the file system.
   */
  private fun openForWrite(
    applicationName: String,
    sha256: ByteString,
    nowEpochMs: Long,
    isManifest: Boolean,
    manifestFreshAtMs: Long? = null,
  ): Files {
    val manifestForApplicationName = if (isManifest) {
      applicationName
    } else {
      null
    }

    val freshAtEpochMs = if (isManifest) {
      manifestFreshAtMs
    } else {
      null
    }

    // Go from absent to DIRTY.
    database.filesQueries.insert(
      sha256_hex = sha256.hex(),
      manifest_for_application_name = manifestForApplicationName,
      file_state = FileState.DIRTY,
      size_bytes = 0L,
      last_used_at_epoch_ms = nowEpochMs,
      fresh_at_epoch_ms = freshAtEpochMs,
    )
    val metadata = getOrNull(sha256)!!

    // Optimistically pin file, if the load fails it will be unpinned.
    createPinIfNotExists(applicationName, metadata.id)

    return metadata
  }

  private fun createPinIfNotExists(
    applicationName: String,
    fileId: Long,
  ) {
    database.pinsQueries.get_pin(fileId, applicationName).executeAsOneOrNull()
      ?: database.pinsQueries.create_pin(fileId, applicationName)
  }

  /**
   * Changes the state of [metadata] from `DIRTY` to `READY`. The caller is no longer the exclusive
   * owner of this file. It may be deleted later to enforce the [maxSizeInBytes] constraint.
   *
   * This cache does not guarantee that the file will still exist after the call to [setReady]. In
   * particular, a file that exceeds the cache [maxSizeInBytes] will be deleted before this function
   * returns. Load the file before calling this method if that's problematic.
   */
  private fun setReady(
    metadata: Files,
    fileSizeBytes: Long,
    nowEpochMs: Long,
  ) {
    database.transaction {
      // Go from DIRTY to READY.
      require(getOrNull(metadata.id)?.file_state == FileState.DIRTY) {
        "[fileName=${metadata.sha256_hex}] can not be set to READY, it is not DIRTY. Could multiple processes be sharing a cache?"
      }

      database.filesQueries.update(
        id = metadata.id,
        file_state = FileState.READY,
        size_bytes = fileSizeBytes,
        last_used_at_epoch_ms = nowEpochMs,
      )
    }

    prune()
  }

  private fun getOrNull(
    sha256: ByteString,
  ): Files? = database.filesQueries.get(sha256.hex()).executeAsOneOrNull()

  private fun getOrNull(
    id: Long,
  ): Files? = database.filesQueries.getById(id).executeAsOneOrNull()

  /**
   * Call this when opening the cache to clean up anything left behind by the previous run.
   *
   * This will prune the cache, necessary if this run's [maxSizeInBytes] is lower than the previous
   * runs value for that parameter.
   *
   * It will also delete dirty files that were open when the previous run completed.
   */
  private fun initialize() {
    try {
      deleteDirtyFiles()
      prune()
    } catch (e: Exception) {
      hasWriteFailures = true // Mark this cache as broken.
      loaderEventListener.cacheStorageFailed(null, e)
    }
  }

  private fun deleteDirtyFiles() {
    while (true) {
      val dirtyFile = database.filesQueries.selectAnyDirtyFile().executeAsOneOrNull() ?: return
      fileSystem.delete(path(dirtyFile))
      database.filesQueries.delete(dirtyFile.id)
    }
  }

  /**
   * Prune is also called when any file transitions from `DIRTY` to `READY` since that file is now
   * included in limit calculations.
   *
   * Note: if a single file is larger than [maxSizeInBytes], it will be deleted immediately upon
   * calls to [setReady]. Callers must open such files for read before marking them as ready,
   * assuming UNIX filesystem semantics where open files are not deleted from under processes that
   * have opened them.
   */
  internal fun prune(maxSizeInBytes: Long = this.maxSizeInBytes) {
    while (true) {
      val currentSize = database.filesQueries.selectCacheSumBytes().executeAsOne().SUM ?: 0L
      if (currentSize <= maxSizeInBytes) return

      val toDelete = database.filesQueries.selectOldestReady().executeAsOneOrNull() ?: return

      fileSystem.delete(path(toDelete))
      database.filesQueries.delete(toDelete.id)
    }
  }

  /** Returns the number of files in the cache DB. */
  internal fun countFiles() = database.filesQueries.count().executeAsOne().toInt()

  /** Returns the number of pins in the cache DB. */
  internal fun countPins() = database.pinsQueries.count().executeAsOne().toInt()

  private fun path(metadata: Files): Path = directory / "entry-${metadata.id}.bin"

  /**
   * Update file record freshAt timestamp to reflect that the manifest is still seen as fresh.
   */
  internal fun updateManifestFreshAt(
    applicationName: String,
    loadedManifest: LoadedManifest,
    nowEpochMs: Long,
  ) {
    if (hasWriteFailures) return // This cache is broken.

    try {
      val freshAtMs = loadedManifest.freshAtEpochMs
      val manifestMetadata = getOrPutManifest(
        applicationName = applicationName,
        content = loadedManifest.manifestBytes,
        putFreshAtMs = freshAtMs,
        nowEpochMs = nowEpochMs,
      )
      database.filesQueries.updateFresh(
        id = manifestMetadata.id,
        fresh_at_epoch_ms = freshAtMs,
      )
    } catch (e: Exception) {
      hasWriteFailures = true // Mark this cache as broken.
      loaderEventListener.cacheStorageFailed(applicationName, e)
    }
  }

  internal companion object {
    internal operator fun invoke(
      sqlDriverFactory: SqlDriverFactory,
      fileSystem: FileSystem,
      directory: Path,
      maxSizeInBytes: Long,
      loaderEventListener: LoaderEventListener,
    ): ZiplineCache {
      val (driver, hasWriteFailures) = try {
        fileSystem.createDirectories(directory, mustCreate = false)
        sqlDriverFactory.create(directory / "zipline.db", Database.Schema) to false
      } catch (e: Exception) {
        loaderEventListener.cacheStorageFailed(null, e)
        NullSqlDriver() to true
      }

      val database = createDatabase(driver = driver)
      val cache = ZiplineCache(
        driver = driver,
        database = database,
        fileSystem = fileSystem,
        directory = directory,
        maxSizeInBytes = maxSizeInBytes,
        loaderEventListener = loaderEventListener,
        hasWriteFailures = hasWriteFailures,
      )
      cache.initialize()
      return cache
    }
  }
}
