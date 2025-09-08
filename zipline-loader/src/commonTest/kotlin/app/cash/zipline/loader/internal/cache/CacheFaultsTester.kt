/*
 * Copyright (C) 2024 Cash App
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
package app.cash.zipline.loader.internal.cache

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import app.cash.zipline.ZiplineManifest
import app.cash.zipline.loader.LoaderEventListener
import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.internal.fetcher.LoadedManifest
import app.cash.zipline.loader.randomToken
import app.cash.zipline.loader.testSqlDriverFactory
import assertk.assertThat
import assertk.assertions.isIn
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import okio.Sink
import okio.use

/**
 * Test [ZiplineCache] by injecting faults after an arbitrary number of writes. This is used to
 * synthesize a full disk, where writes stop working when the disk is full.
 *
 * We support injecting failures for these writes:
 *  - Opening a file to write it: `FileSystem.sink()`
 *  - Closing a file after writing it: `Sink.close()`. This also truncates the file to 0 bytes.
 *  - SQL executes.
 *
 * It overlays these failures with the following operations, potentially performed multiple times:
 *  - opening the cache
 *  - loading an application
 *
 * Loading the application may be successful or not. Successful loads are pinned; unsuccessful ones
 * are not pinned. The load may also be fresh or not. Note that the cache implements storage only
 * and not freshness policy.
 */
class CacheFaultsTester {
  private val fileSystem = FileSystem.SYSTEM
  private val directory =
    FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "CacheFaultsTester-${randomToken().hex()}"
  private var nowMillis = 1_000L

  private val applicationName = "red"

  /** Adjust this to trigger cache pruning. */
  var cacheSize = Long.MAX_VALUE

  /** Increment this to invalidate the cached code. */
  var manifestVersion = 0

  private val moduleName: String
    get() = "$applicationName-$manifestVersion"
  private val moduleContent: ByteString
    get() = "I am the Zipline file for manifestVersion=$manifestVersion".encodeUtf8()
  private val moduleContentSha256: ByteString
    get() = moduleContent.sha256()

  var downloadCount = 0
    private set

  /** This includes both direct file system and SQL writes. */
  var fileSystemWriteCount = 0
    private set

  /**
   * How many writes to permit until all further writes fail.
   *
   * Note that file deletes are always allowed, but SQL deletes are still limited because they must
   * be journaled.
   */
  var fileSystemWriteLimit = Int.MAX_VALUE

  /**
   * Returns the names of the files managed by this cache, excluding SQLite temporary files.
   * https://www.sqlite.org/tempfiles.html
   */
  val fileNames: List<String>
    get() = (fileSystem.listOrNull(directory) ?: listOf())
      .map { it.name }
      .filterNot { it.startsWith("zipline.db-") }

  /**
   * Incremented on each failure. Note that once a cache marks itself as degraded, this won't be
   * incremented until the cache is rebuilt.
   */
  var storageFailureCount = 0

  suspend fun withCache(block: suspend Session.() -> Unit) {
    val cache = ZiplineCache(
      sqlDriverFactory = LimitWritesSqlDriverFactory(),
      fileSystem = LimitWritesFileSystem(fileSystem),
      directory = directory,
      maxSizeInBytes = cacheSize,
      loaderEventListener = object : LoaderEventListener() {
        override fun cacheStorageFailed(applicationName: String?, e: Exception) {
          storageFailureCount++
        }
      },
    )

    try {
      Session(cache).block()
    } finally {
      cache.close()
    }
  }

  inner class Session internal constructor(
    private val cache: ZiplineCache,
  ) {
    suspend fun loadApp(
      loadSuccess: Boolean = true,
      cachedResultIsFresh: Boolean = true,
    ) {
      val cachedManifest = cache.getPinnedManifest(applicationName, nowMillis)
      val loadedManifest = when {
        cachedManifest != null && cachedResultIsFresh -> cachedManifest
        else -> downloadManifest()
      }

      for (module in loadedManifest.manifest.modules) {
        val loadedZiplineFile = cache.getOrPut(
          applicationName = applicationName,
          sha256 = module.value.sha256,
          nowEpochMs = nowMillis,
          download = ::downloadZiplineFile,
        )
        assertThat(loadedZiplineFile).isIn(null, moduleContent)
      }

      if (loadSuccess) {
        cache.pinManifest(applicationName, loadedManifest, nowMillis)
        cache.updateManifestFreshAt(applicationName, loadedManifest, nowMillis)
      } else {
        cache.unpinManifest(applicationName, loadedManifest, nowMillis)
      }
    }

    private fun downloadManifest(): LoadedManifest {
      downloadCount++

      val manifest = ZiplineManifest.create(
        modules = mapOf(
          moduleName to ZiplineManifest.Module(
            url = "$moduleName.zipline",
            sha256 = moduleContentSha256,
          ),
        ),
      )

      return LoadedManifest(
        manifestBytes = manifest.encodeJson().encodeUtf8(),
        manifest = manifest,
        freshAtEpochMs = nowMillis,
      )
    }

    private fun downloadZiplineFile(): ByteString {
      downloadCount++
      return moduleContent
    }
  }

  private inner class LimitWritesFileSystem(
    delegate: FileSystem,
  ) : ForwardingFileSystem(delegate) {
    override fun createDirectory(dir: Path, mustCreate: Boolean) {
      fileSystemWriteCount++
      if (fileSystemWriteCount >= fileSystemWriteLimit) throw IOException("write limit exceeded")

      super.createDirectory(dir, mustCreate)
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
      fileSystemWriteCount++
      if (fileSystemWriteCount >= fileSystemWriteLimit) throw IOException("write limit exceeded")

      return LimitWritesSink(
        path = file,
        delegate = super.sink(file, mustCreate),
      )
    }
  }

  private inner class LimitWritesSink(
    val path: Path,
    val delegate: Sink,
  ) : Sink by delegate {
    override fun close() {
      delegate.close()

      fileSystemWriteCount++
      if (fileSystemWriteCount >= fileSystemWriteLimit) {
        truncateFileToEmpty()
        throw IOException("write limit exceeded")
      }
    }

    private fun truncateFileToEmpty() {
      fileSystem.openReadWrite(path).use {
        it.resize(0L)
      }
    }
  }

  private inner class LimitWritesSqlDriverFactory : SqlDriverFactory {
    override fun create(
      path: Path,
      schema: SqlSchema<QueryResult.Value<Unit>>,
    ): SqlDriver {
      fileSystemWriteCount++
      if (fileSystemWriteCount >= fileSystemWriteLimit) {
        throw SqlFaultException("write limit exceeded")
      }

      return LimitWritesSqlDriver(
        testSqlDriverFactory().create(
          path = directory / "zipline.db",
          schema = Database.Schema,
        ),
      )
    }
  }

  private inner class LimitWritesSqlDriver(
    private val delegate: SqlDriver,
  ) : SqlDriver by delegate {
    override fun execute(
      identifier: Int?,
      sql: String,
      parameters: Int,
      binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
      fileSystemWriteCount++
      if (fileSystemWriteCount >= fileSystemWriteLimit) {
        throw SqlFaultException("write limit exceeded")
      }

      return delegate.execute(identifier, sql, parameters, binders)
    }
  }

  private class SqlFaultException(message: String) : Exception(message)
}
