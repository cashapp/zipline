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
import app.cash.zipline.loader.strategy.EmbeddedCacheThenNetworkStrategy
import app.cash.zipline.loader.strategy.DownloadOnlyStrategy
import app.cash.zipline.loader.strategy.LoadStrategy
import com.squareup.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Gets code from an HTTP server or a local cache,
 * and loads it into a zipline instance. This attempts
 * to load code as quickly as possible, and will
 * concurrently download and load code.
 */
class ZiplineLoader(
  private val dispatcher: CoroutineDispatcher,
  private val httpClient: ZiplineHttpClient,
  private val embeddedDirectory: Path,
  private val embeddedFileSystem: FileSystem,
  cacheDbDriver: SqlDriver, // SqlDriver is already initialized to the platform and SQLite DB on disk
  cacheDirectory: Path,
  val cacheFileSystem: FileSystem,
  cacheMaxSizeInBytes: Int = 100 * 1024 * 1024,
  nowMs: () -> Long, // 100 MiB
) {
  private var concurrentDownloadsSemaphore = Semaphore(3)
  var concurrentDownloads = 3
    set(value) {
      require(value > 0)
      field = value
      concurrentDownloadsSemaphore = Semaphore(value)
    }

  // TODO add schema version checker and automigration for non-Android platforms
  private val cache = createZiplineCache(
    driver = cacheDbDriver,
    fileSystem = cacheFileSystem,
    directory = cacheDirectory,
    maxSizeInBytes = cacheMaxSizeInBytes.toLong(),
    nowMs = nowMs
  )

  suspend fun download(
    manifestUrl: String,
    downloadDirectory: Path,
  ) {
    cacheFileSystem.createDirectories(downloadDirectory)

    val manifest = downloadZiplineManifest(manifestUrl)

    val manifestPath = downloadDirectory / PREBUILT_MANIFEST_FILE_NAME
    cacheFileSystem.write(manifestPath) {
      writeUtf8(Json.encodeToString(manifest))
    }

    val strategy = DownloadOnlyStrategy(
      httpClient = httpClient,
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      fileSystem = cacheFileSystem,
      downloadDirectory = downloadDirectory
    )

    loadImpl(manifest, strategy)
  }

  suspend fun load(zipline: Zipline, manifestUrl: String) {
    val manifest = downloadZiplineManifest(manifestUrl)

    val strategy = EmbeddedCacheThenNetworkStrategy(
      zipline = zipline,
      httpClient = httpClient,
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      embeddedFileSystem = embeddedFileSystem,
      embeddedDirectory = embeddedDirectory,
      cache = cache
    )

    loadImpl(manifest, strategy)
  }

  private suspend fun downloadZiplineManifest(manifestUrl: String): ZiplineManifest {
    val manifestByteString = try {
      concurrentDownloadsSemaphore.withPermit {
        httpClient.download(manifestUrl)
      }
    } catch (e: IOException) {
      // If manifest fails to load over network, fallback to prebuilt in resources
      val prebuiltManifestPath = embeddedDirectory / PREBUILT_MANIFEST_FILE_NAME
      embeddedFileSystem.read(prebuiltManifestPath) {
        readByteString()
      }
    }

    return Json.decodeFromString(manifestByteString.utf8())
  }

  suspend fun load(
    zipline: Zipline,
    manifest: ZiplineManifest,
  ) {
    val strategy = EmbeddedCacheThenNetworkStrategy(
      zipline = zipline,
      httpClient = httpClient,
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      embeddedFileSystem = embeddedFileSystem,
      embeddedDirectory = embeddedDirectory,
      cache = cache
    )

    loadImpl(manifest, strategy)
  }

  private suspend fun loadImpl(
    manifest: ZiplineManifest,
    strategy: LoadStrategy,
  ) {
    coroutineScope {
      val loads = manifest.modules.map {
        ModuleLoad(it.key, it.value, strategy)
      }
      for (load in loads) {
        val loadJob = launch { load.load() }

        val downstreams = loads.filter { load.id in it.module.dependsOnIds }
        for (downstream in downstreams) {
          downstream.upstreams += loadJob
        }
      }
    }
  }

  private inner class ModuleLoad(
    val id: String,
    val module: ZiplineModule,
    val strategy: LoadStrategy,
  ) {
    val upstreams = mutableListOf<Job>()

    /**
     * Follow given strategy to get ZiplineFile and then process
     */
    suspend fun load() {
      val ziplineFile = strategy.getZiplineFile(id, module.sha256, module.url)
      upstreams.joinAll()
      withContext(dispatcher) {
        strategy.processFile(ziplineFile, id, module.sha256)
      }
    }
  }

  companion object {
    const val PREBUILT_MANIFEST_FILE_NAME = "manifest.zipline.json"
  }
}
