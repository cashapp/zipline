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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.Path

/**
 * Gets code from an HTTP server or a local cache,
 * and loads it into a zipline instance. This attempts
 * to load code as quickly as possible, and will
 * concurrently download and load code.
 */
class ZiplineLoader(
  val dispatcher: CoroutineDispatcher,
  val client: ZiplineHttpClient,
  val cacheDirectory: Path,
  val cacheMaxSizeInBytes: Int = 100 * 1024 * 1024,
) {
  private var concurrentDownloadsSemaphore = Semaphore(3)
  var concurrentDownloads = 3
    set(value) {
      require(value > 0)
      field = value
      concurrentDownloadsSemaphore = Semaphore(value)
    }

  suspend fun load(scope: CoroutineScope, zipline: Zipline, url: String) {
    val manifestString = concurrentDownloadsSemaphore.withPermit {
      client.download(url).utf8()
    }
    val manifest = Json.decodeFromString<ZiplineManifest>(manifestString)
    load(scope, zipline, manifest)
  }

  suspend fun load(scope: CoroutineScope, zipline: Zipline, manifest: ZiplineManifest) {
    val loads = manifest.modules.map {
      ModuleLoad(zipline, it.key, it.value)
    }

    for (load in loads) {
      val deferred: Deferred<*> = scope.async {
        load.load()
      }

      val downstreams = loads.filter { load.id in it.module.dependsOnIds }

      for (downstream in downstreams) {
        downstream.upstreams += deferred
      }
    }
  }

  private inner class ModuleLoad(
    val zipline: Zipline,
    val id: String,
    val module: ZiplineModule,
  ) {
    val upstreams = mutableListOf<Deferred<*>>()

    suspend fun load() {
      val download = concurrentDownloadsSemaphore.withPermit {
        client.download(module.url)
      }
      for (upstream in upstreams) {
        upstream.await()
      }
      withContext(dispatcher) {
        zipline.loadJsModule(download.toByteArray(), id)
      }
    }
  }

  /** For downloading patches instead of full-sized files. */
  suspend fun localFileHashes(): List<ByteString> {
    val cacheDir = File(cacheDirectory)
  }
}

