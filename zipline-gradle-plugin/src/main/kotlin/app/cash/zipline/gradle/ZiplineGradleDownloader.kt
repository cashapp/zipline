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

package app.cash.zipline.gradle

import app.cash.zipline.loader.ManifestVerifier.Companion.NO_SIGNATURE_CHECKS
import app.cash.zipline.loader.ZiplineLoader
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path.Companion.toOkioPath

internal class ZiplineGradleDownloader {
  private val executorService = Executors.newSingleThreadExecutor { Thread(it, "Zipline") }
  private val dispatcher = executorService.asCoroutineDispatcher()
  private val client = OkHttpClient()

  fun download(downloadDir: File, applicationName: String, manifestUrl: String) {
    val ziplineLoader = ZiplineLoader(
      dispatcher = dispatcher,
      manifestVerifier = NO_SIGNATURE_CHECKS,
      httpClient = client,
    )
    runBlocking {
      ziplineLoader.download(
        applicationName = applicationName,
        downloadFileSystem = FileSystem.SYSTEM,
        downloadDir = downloadDir.toOkioPath(),
        manifestUrl = manifestUrl,
      )
    }
  }
}
