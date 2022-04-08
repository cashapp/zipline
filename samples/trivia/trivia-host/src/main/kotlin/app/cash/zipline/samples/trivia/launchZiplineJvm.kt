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
package app.cash.zipline.samples.trivia

import app.cash.zipline.Zipline
import app.cash.zipline.loader.DriverFactory
import app.cash.zipline.loader.OkHttpZiplineHttpClient
import app.cash.zipline.loader.ZiplineLoader
import app.cash.zipline.loader.createZiplineCache
import app.cash.zipline.loader.fetcher.FsCachingFetcher
import app.cash.zipline.loader.fetcher.FsEmbeddedFetcher
import app.cash.zipline.loader.fetcher.HttpFetcher
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path.Companion.toPath

fun getTriviaService(zipline: Zipline): TriviaService {
  return zipline.take("triviaService")
}

suspend fun launchZipline(dispatcher: CoroutineDispatcher): Zipline {
  val zipline = Zipline.create(dispatcher)
  val manifestUrl = "http://localhost:8080/manifest.zipline.json"
  val cacheDir = "zipline-cache".toPath()
  val driver = DriverFactory().createDriver()
  val embeddedDir = "/".toPath()
  val httpClient = OkHttpZiplineHttpClient(OkHttpClient())
  val loader = ZiplineLoader(
    dispatcher = dispatcher,
    httpClient = httpClient,
    fetchers = listOf(
      FsEmbeddedFetcher(
        embeddedDir = embeddedDir,
        embeddedFileSystem = FileSystem.RESOURCES,
      ),
      FsCachingFetcher(
        cache = createZiplineCache(
          driver = driver,
          fileSystem = FileSystem.SYSTEM,
          directory = cacheDir,
          nowMs = System::currentTimeMillis,
        ),
        delegate = HttpFetcher(httpClient = httpClient),
      ),
    ),
  )
  loader.load(zipline, manifestUrl)
  val moduleName = "./zipline-root-trivia-js.js"
  zipline.quickJs.evaluate(
    "require('$moduleName').app.cash.zipline.samples.trivia.launchZipline()",
    "launchZiplineJvm.kt"
  )
  return zipline
}
