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

import app.cash.zipline.loader.DriverFactory
import app.cash.zipline.loader.OkHttpZiplineHttpClient
import app.cash.zipline.loader.ZiplineLoader
import java.io.File
import java.time.Clock
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath

class ZiplineDownloader {
  private val executorService = Executors.newSingleThreadExecutor { Thread(it, "Zipline") }
  private val dispatcher = executorService.asCoroutineDispatcher()
  private val client = OkHttpClient()

  // Dummy base url
  private val baseUrl = "http://10.0.2.2:8080/".toHttpUrl()

  // Run SQLite in memory
  private val driver = DriverFactory().createDriver()

  fun download(manifestUrl: String, downloadDir: File) {
    val ziplineLoader = ZiplineLoader(
      dispatcher = dispatcher,
      httpClient = OkHttpZiplineHttpClient(baseUrl, client),
      cacheFileSystem = FileSystem.SYSTEM,
      embeddedFileSystem = FileSystem.RESOURCES,
      embeddedDirectory = "/".toPath(),
      cacheDirectory = downloadDir.toPath().toOkioPath(),
      cacheDbDriver = driver,
      nowMs = { Clock.systemDefaultZone().instant().toEpochMilli() },
    )
    runBlocking {
      ziplineLoader.download(manifestUrl, downloadDir.toOkioPath())
    }
  }
}
