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
import app.cash.zipline.loader.interceptors.getter.FsCacheGetterInterceptor
import app.cash.zipline.loader.interceptors.getter.FsEmbeddedGetterInterceptor
import app.cash.zipline.loader.interceptors.getter.GetterInterceptor
import app.cash.zipline.loader.interceptors.getter.HttpGetterInterceptor
import app.cash.zipline.loader.interceptors.getter.HttpPutInFsCacheGetterInterceptor
import app.cash.zipline.loader.interceptors.handler.FsSaveHandlerInterceptor
import app.cash.zipline.loader.interceptors.handler.HandlerInterceptor
import app.cash.zipline.loader.interceptors.handler.ZiplineLoadHandlerInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * Gets code from an HTTP server or a local cache,
 * and loads it into a zipline instance. This attempts
 * to load code as quickly as possible, and will
 * concurrently download and load code.
 */
internal class ZiplineModuleLoader private constructor(
  private val dispatcher: CoroutineDispatcher,
  private val getterInterceptors: List<GetterInterceptor>,
  private val handlerInterceptors: List<HandlerInterceptor>,
) {
  suspend fun load(
    manifest: ZiplineManifest
  ) {
    coroutineScope {
      val loads = manifest.modules.map {
        ModuleLoad(it.key, it.value)
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
  ) {
    val upstreams = mutableListOf<Job>()

    /**
     * Follow given strategy to get ZiplineFile and then process
     */
    suspend fun load() {
      var ziplineFile: ZiplineFile? = null
      for (interceptor in getterInterceptors) {
        ziplineFile = interceptor.get(id, module.sha256, module.url)
        if (ziplineFile != null) break
      }
      if (ziplineFile == null) throw IllegalStateException("Unable to get ZiplineFile for [module=$module]")

      upstreams.joinAll()
      withContext(dispatcher) {
        for (interceptor in handlerInterceptors) {
          interceptor.handle(ziplineFile, id, module.sha256)
        }
      }
    }
  }

  companion object {
    fun createDownloadOnly(
      dispatcher: CoroutineDispatcher,
      httpClient: ZiplineHttpClient,
      concurrentDownloadsSemaphore: Semaphore,
      downloadDir: Path,
      downloadFileSystem: FileSystem,
    ) = ZiplineModuleLoader(
      dispatcher = dispatcher,
      getterInterceptors = listOf(
        HttpGetterInterceptor(
          httpClient = httpClient,
          concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
        )
      ),
      handlerInterceptors = listOf(
        FsSaveHandlerInterceptor(
          downloadFileSystem = downloadFileSystem,
          downloadDir = downloadDir
        )
      )
    )

    fun createProduction(
      dispatcher: CoroutineDispatcher,
      httpClient: ZiplineHttpClient,
      concurrentDownloadsSemaphore: Semaphore,
      embeddedDir: Path,
      embeddedFileSystem: FileSystem,
      cache: ZiplineCache,
      zipline: Zipline,
    ) = ZiplineModuleLoader(
      dispatcher = dispatcher,
      getterInterceptors = listOf(
        FsEmbeddedGetterInterceptor(
          embeddedDirectory = embeddedDir,
          embeddedFileSystem = embeddedFileSystem
        ),
        FsCacheGetterInterceptor(
          cache = cache,
        ),
        HttpPutInFsCacheGetterInterceptor(
          httpClient = httpClient,
          concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
          cache = cache,
        )
      ),
      handlerInterceptors = listOf(
        ZiplineLoadHandlerInterceptor(
          zipline = zipline
        )
      ),
    )
  }
}
