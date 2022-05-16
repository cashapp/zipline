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
  private val database: Database,
  private val fileSystem: FileSystem,
  private val directory: Path,
  private val maxSizeInBytes: Long,
  private val nowMs: () -> Long,
) {
  fun write(applicationId: String, sha256: ByteString, content: ByteString) {
    val metadata = openForWrite(applicationId, sha256, false) ?: return
    write(metadata, content)
  }

  private fun write(metadata: Files, content: ByteString) {
    fileSystem.createDirectories(directory)
    fileSystem.write(path(metadata)) {
      write(content)
    }
    setReady(metadata, content.size.toLong())
  }

  /** @return null if there is no pinned manifest */
  fun getPinnedManifest(applicationId: String): ZiplineManifest? {
    val manifestFile = database.filesQueries
      .selectPinnedManifest(applicationId)
      .executeAsOneOrNull() ?: return null
    val manifestByteString = read(manifestFile.sha256_hex)
      ?: throw FileNotFoundException(
        "No manifest file on disk with [fileName=${manifestFile.sha256_hex}]"
      )
    return Json.decodeFromString(ZiplineManifest.serializer(), manifestByteString.utf8())
  }

  /** Pins manifest and unpins all other files and manifests */
  fun pinManifest(applicationId: String, manifest: ZiplineManifest) {
    val manifestByteString = Json.encodeToString(ZiplineManifest.serializer(), manifest).encodeUtf8()
    val manifestMetadata = writeManifest(applicationId, manifestByteString.sha256(), manifestByteString)
      ?: return

    val existingPins = database.pinsQueries.get_pins(applicationId).executeAsList()

    // Pin all modules in this manifest
    val moduleMetadata = manifest.modules.map { (_, module) ->
      database.filesQueries.get(module.sha256.hex()).executeAsOneOrNull() ?: return
    }
    moduleMetadata.map { metadata ->
      createPinIfNotExists(metadata.id, applicationId)
    }

    // Pin the manifest
    createPinIfNotExists(manifestMetadata.id, applicationId)

    // Remove pins for last stable application pin
    database.pinsQueries.delete_pin(applicationId, existingPins.map { it.file_id })
  }

  private fun writeManifest(applicationId: String, sha256: ByteString, content: ByteString): Files? {
    val metadata = openForWrite(applicationId, sha256, true) ?: return null
    write(metadata, content)
    return database.filesQueries.get(metadata.sha256_hex).executeAsOne()
  }

  /**
   * Handle competing read and download.
   *
   * If file is downloading, reading thread will suspend until download is complete.
   *
   * Concurrent downloads on the same [sha256] are not ever done. One thread loses and suspends.
   */
  suspend fun getOrPut(applicationId: String, sha256: ByteString, download: suspend () -> ByteString): ByteString {
    val read = read(sha256)
    if (read != null) return read

    val contents = download()
    write(applicationId, sha256, contents)
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



  /**
   * Returns file metadata if the file was absent and is now `DIRTY`. The caller is now the exclusive owner of this file
   * and should proceed to write the file to the file system.
   *
   * @param isManifest set to the application id for only manifest files
   */
  private fun openForWrite(
    applicationId: String,
    sha256: ByteString,
    isManifest: Boolean,
  ): Files? = try {
    val manifestForApplicationId = if (isManifest) {
      applicationId
    } else {
      null
    }

    // Go from absent to DIRTY.
    database.filesQueries.insert(
      sha256_hex = sha256.hex(),
      manifest_for_application_id = manifestForApplicationId,
      file_state = FileState.DIRTY,
      size_bytes = 0L,
      last_used_at_epoch_ms = nowMs()
    )
    val metadata = getOrNull(sha256)!!

    // Optimistically pin file, if the load fails it will be unpinned
    createPinIfNotExists(metadata.id, applicationId)

    metadata
  } catch (e: Exception) {
    if (!isSqlException(e)) throw e
    null // Presumably the file is already dirty.
  }

  private fun createPinIfNotExists(
    file_id: Long,
    application_id: String
  ) {
    if (database.pinsQueries.get_pin(file_id, application_id).executeAsOneOrNull() == null) {
      database.pinsQueries.create_pin(file_id, application_id)
    }
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
    metadata: Files,
    fileSizeBytes: Long
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
    while (true) {
      val currentSize = database.filesQueries.selectCacheSumBytes().executeAsOne().SUM ?: 0L
      if (currentSize <= maxSizeInBytes) return

      val toDelete = database.filesQueries.selectOldestReady().executeAsOneOrNull() ?: return

      fileSystem.delete(path(toDelete))
      database.filesQueries.delete(toDelete.id)
    }
  }

  private fun path(metadata: Files): Path {
    return directory / "entry-${metadata.id}.bin"
  }
}

// TODO add schema version checker and automigration for non-Android platforms
fun createZiplineCache(
  driver: SqlDriver,
  fileSystem: FileSystem,
  directory: Path,
  maxSizeInBytes: Int = 100 * 1024 * 1024,
  nowMs: () -> Long,
): ZiplineCache {
  val database = createDatabase(driver)
  val ziplineCache = ZiplineCache(
    database = database,
    fileSystem = fileSystem,
    directory = directory,
    maxSizeInBytes = maxSizeInBytes.toLong(),
    nowMs = nowMs,
  )
  ziplineCache.prune()
  return ziplineCache
}
