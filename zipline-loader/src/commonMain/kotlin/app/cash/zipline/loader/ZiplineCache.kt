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

import app.cash.zipline.EventListener
import app.cash.zipline.loader.ZiplineManifest.Companion.decodeToZiplineManifest
import com.squareup.sqldelight.db.SqlDriver
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
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
  private val eventListener: EventListener,
  private val database: Database,
  private val fileSystem: FileSystem,
  private val directory: Path,
  private val maxSizeInBytes: Long,
  private val nowMs: () -> Long,
) {
  fun write(applicationName: String, sha256: ByteString, content: ByteString) {
    val metadata = openForWrite(applicationName, sha256, false) ?: return
    write(metadata, content)
  }

  private fun write(metadata: Files, content: ByteString) {
    fileSystem.createDirectories(directory)
    fileSystem.write(path(metadata)) {
      write(content)
    }
    setReady(metadata, content.size.toLong())
  }

  /**
   * Handle competing read and download.
   *
   * If file is downloading, reading thread will suspend until download is complete.
   *
   * Concurrent downloads on the same [sha256] are not ever done. One thread loses and suspends.
   */
  suspend fun getOrPut(
    applicationName: String,
    sha256: ByteString,
    download: suspend () -> ByteString?
  ): ByteString? {
    val read = read(sha256)
    if (read != null) return read

    val contents = download() ?: return null
    write(applicationName, sha256, contents)
    return contents
  }

  fun read(
    sha256: ByteString
  ) = read(sha256.hex())

  fun read(
    sha256hex: String
  ): ByteString? {
    val metadata = database.filesQueries.get(sha256hex).executeAsOneOrNull() ?: return null
    if (metadata.file_state != FileState.READY) return null
    // Update the used at timestamp
    database.filesQueries.update(
      id = metadata.id,
      file_state = metadata.file_state,
      size_bytes = metadata.size_bytes,
      last_used_at_epoch_ms = nowMs(),
    )
    return try {
      fileSystem.read(path(metadata)) {
        readByteString()
      }
    } catch (e: FileNotFoundException) {
      null // Might have been pruned while we were trying to read.
    }
  }

  internal fun pin(applicationName: String, sha256: ByteString) {
    val fileId = database.filesQueries.get(sha256.hex()).executeAsOneOrNull()?.id ?: return
    createPinIfNotExists(applicationName, fileId)
  }

  internal fun unpin(applicationName: String, sha256: ByteString) {
    val fileId = database.filesQueries.get(sha256.hex()).executeAsOneOrNull()?.id ?: return
    database.pinsQueries.delete_pin(applicationName, fileId)
  }

  /** Returns null if there is no pinned manifest. */
  fun getPinnedManifest(applicationName: String): ZiplineManifest? {
    val manifestFile = database.filesQueries
      .selectPinnedManifest(applicationName)
      .executeAsOneOrNull() ?: return null
    val manifestByteString = read(manifestFile.sha256_hex)
      ?: throw FileNotFoundException(
        "No manifest file on disk with [fileName=${manifestFile.sha256_hex}]"
      )
    return Json.decodeFromString(ZiplineManifest.serializer(), manifestByteString.utf8())
  }

  /** Pins manifest and unpins all other files and manifests */
  fun pinManifest(applicationName: String, manifest: ZiplineManifest) {
    val manifestByteString = Json
      .encodeToString(ZiplineManifest.serializer(), manifest).encodeUtf8()
    val manifestMetadata =
      writeManifest(applicationName, manifestByteString.sha256(), manifestByteString)
        ?: return

    database.transaction {
      database.pinsQueries.delete_application_pins(applicationName)

      // Pin all modules in this manifest.
      manifest.modules.forEach { (_, module) ->
        database.filesQueries.get(module.sha256.hex()).executeAsOneOrNull()?.let { metadata ->
          createPinIfNotExists(applicationName, metadata.id)
        }
      }

      // Pin the manifest.
      createPinIfNotExists(applicationName, manifestMetadata.id)
    }
  }

  /**
   * Unpin manifest and make all files open to pruning, except those included
   * in another pinned manifest.
   */
  fun unpinManifest(applicationName: String, manifest: ZiplineManifest) {
    val unpinManifestByteString = Json
      .encodeToString(ZiplineManifest.serializer(), manifest)
      .encodeUtf8()
    val unpinManifestFile = database.filesQueries
      .get(unpinManifestByteString.sha256().hex())
      .executeAsOneOrNull()

    // Get fallback manifest metadata.
    val fallbackManifestFile: Files? = unpinManifestFile?.let {
      database.filesQueries
        .selectPinnedManifestNotFileId(applicationName, it.id)
        .executeAsOneOrNull()
    } ?: database.filesQueries
      .selectPinnedManifest(applicationName)
      .executeAsOneOrNull()

    if (fallbackManifestFile == null) {
      // There is no fallback manifest, delete all pins and return.
      database.pinsQueries.delete_application_pins(applicationName)
    } else {
      // Pin the fallback manifest, which removes all pins prior to pinning.
      val fallbackManifest = read(fallbackManifestFile.sha256_hex)
        ?.decodeToZiplineManifest(eventListener, applicationName, "cache-read")
        ?: throw FileNotFoundException(
          "No manifest file on disk with [fileName=${fallbackManifestFile.sha256_hex}]"
        )
      pinManifest(applicationName, fallbackManifest)
    }
  }

  internal fun writeManifest(
    applicationName: String,
    sha256: ByteString,
    content: ByteString,
  ): Files? = getOrNull(sha256)
    ?: openForWrite(applicationName, sha256, true)?.let { metadata ->
      write(metadata, content)
      database.filesQueries.get(metadata.sha256_hex).executeAsOne()
    }

  /**
   * Returns file metadata if the file was absent and is now `DIRTY`. The caller is now the
   * exclusive owner of this file and should proceed to write the file to the file system.
   */
  private fun openForWrite(
    applicationName: String,
    sha256: ByteString,
    isManifest: Boolean,
  ): Files? = try {
    val manifestForApplicationName = if (isManifest) {
      applicationName
    } else {
      null
    }

    // Go from absent to DIRTY.
    database.filesQueries.insert(
      sha256_hex = sha256.hex(),
      manifest_for_application_name = manifestForApplicationName,
      file_state = FileState.DIRTY,
      size_bytes = 0L,
      last_used_at_epoch_ms = nowMs()
    )
    val metadata = getOrNull(sha256)!!

    // Optimistically pin file, if the load fails it will be unpinned.
    createPinIfNotExists(applicationName, metadata.id)

    metadata
  } catch (e: Exception) {
    if (!isSqlException(e)) throw e
    null // Presumably the file is already dirty.
  }

  private fun createPinIfNotExists(
    application_name: String,
    file_id: Long,
  ) {
    if (database.pinsQueries.get_pin(file_id, application_name).executeAsOneOrNull() == null) {
      database.pinsQueries.create_pin(file_id, application_name)
    }
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
  ) {
    database.transaction {
      // Go from DIRTY to READY.
      require(getOrNull(metadata.id)?.file_state == FileState.DIRTY) {
        "file ${metadata.sha256_hex} was not DIRTY ... multiple processes sharing a cache?"
      }

      database.filesQueries.update(
        id = metadata.id,
        file_state = FileState.READY,
        size_bytes = fileSizeBytes,
        last_used_at_epoch_ms = nowMs(),
      )
    }

    prune()
  }

  /** Deletes the file from the file system and the metadata DB. */
  private fun deleteDirty(
    metadata: Files
  ) {
    // Go from DIRTY to absent.
    fileSystem.delete(path(metadata))
    database.transaction {
      require(getOrNull(metadata.id)?.file_state == FileState.DIRTY) {
        "file ${metadata.id} was not DIRTY ... multiple processes sharing a cache?"
      }
      database.filesQueries.delete(metadata.id)
    }
  }

  private fun getOrNull(
    sha256: ByteString
  ): Files? = database.filesQueries.get(sha256.hex()).executeAsOneOrNull()

  private fun getOrNull(
    id: Long
  ): Files? = database.filesQueries.getById(id).executeAsOneOrNull()

  /**
   * Call this when opening the cache app to handle in-flight failed downloads and limit changes.
   *
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

  private fun path(metadata: Files): Path {
    return directory / "entry-${metadata.id}.bin"
  }
}

// TODO add schema version checker and automigration for non-Android platforms
fun createZiplineCache(
  eventListener: EventListener,
  driver: SqlDriver,
  fileSystem: FileSystem,
  directory: Path,
  maxSizeInBytes: Long = 100L * 1024L * 1024L,
  nowMs: () -> Long,
): ZiplineCache {
  val database = createDatabase(driver)
  val ziplineCache = ZiplineCache(
    eventListener = eventListener,
    database = database,
    fileSystem = fileSystem,
    directory = directory,
    maxSizeInBytes = maxSizeInBytes,
    nowMs = nowMs,
  )
  ziplineCache.prune()
  return ziplineCache
}
