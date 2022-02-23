/*
 * Copyright (C) 2021 Square, Inc.
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

import app.cash.zipline.Zipline
import com.squareup.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Gets code from an HTTP server or a local cache,
 * and loads it into a zipline instance. This attempts
 * to load code as quickly as possible, and will
 * concurrently download and load code.
 */
class ZiplineLoader(
  val dispatcher: CoroutineDispatcher,
  val httpClient: ZiplineHttpClient,
  val fileSystem: FileSystem,
  val resourceFileSystem: FileSystem,
  val resourceDirectory: Path,
  cacheDbDriver: SqlDriver, // SqlDriver is already initialized to the platform and SQLite DB on disk
  cacheDirectory: Path,
  nowMs: () -> Long, // 100 MiB
  cacheMaxSizeInBytes: Int = 100 * 1024 * 1024,
) {
  private var concurrentDownloadsSemaphore = Semaphore(3)
  var concurrentDownloads = 3
    set(value) {
      require(value > 0)
      field = value
      concurrentDownloadsSemaphore = Semaphore(value)
    }

  // TODO add schema version checker and automigration
  private val cache = createZiplineCache(
    driver = cacheDbDriver,
    fileSystem = fileSystem,
    directory = cacheDirectory,
    maxSizeInBytes = cacheMaxSizeInBytes.toLong(),
    nowMs = nowMs
  )

  suspend fun load(zipline: Zipline, url: String) {
    // TODO fallback to manifest shipped in resources for offline support
    val manifestByteString = concurrentDownloadsSemaphore.withPermit {
      httpClient.download(url)
    }

    val manifest = Json.decodeFromString<ZiplineManifest>(manifestByteString.utf8())

    load(zipline, manifest)
  }

  suspend fun load(zipline: Zipline, manifest: ZiplineManifest) {
    coroutineScope {
      val loads = manifest.modules.map {
        ModuleLoad(zipline, it.key, it.value)
      }
      for (load in loads) {
        val deferred: Deferred<*> = async {
          load.load()
        }

        val downstreams = loads.filter { load.id in it.module.dependsOnIds }

        for (downstream in downstreams) {
          downstream.upstreams += deferred
        }
      }
    }
  }

  private inner class ModuleLoad(
    val zipline: Zipline,
    val id: String,
    val module: ZiplineModule,
  ) {
    val upstreams = mutableListOf<Deferred<*>>()

    /** Attempt to load from, in prioritized order: resources, cache, network */
    suspend fun load() {
      val resourcePath = resourceDirectory / module.sha256
      val ziplineFileBytes = if (resourceFileSystem.exists(resourcePath)) {
        resourceFileSystem.read(resourcePath) {
          readByteString()
        }
      } else {
        cache.getOrPut(module.sha256) {
          concurrentDownloadsSemaphore.withPermit {
            httpClient.download(module.url)
          }
        }
      }

      val ziplineFile = ZiplineFile.read(Buffer().write(ziplineFileBytes))
      upstreams.awaitAll()
      withContext(dispatcher) {
        zipline.multiplatformLoadJsModule(ziplineFile.quickjsBytecode.toByteArray(), id)
      }
    }
  }

  /** For downloading patches instead of full-sized files. */
  suspend fun localFileHashes(): List<ByteString> {
    TODO()
  }
}

