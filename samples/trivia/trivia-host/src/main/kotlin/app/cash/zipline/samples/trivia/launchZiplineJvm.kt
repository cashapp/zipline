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
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path.Companion.toPath

fun getTriviaService(zipline: Zipline): TriviaService {
  return zipline.take("triviaService")
}

suspend fun launchZipline(dispatcher: CoroutineDispatcher): Zipline {
  val zipline = Zipline.create(dispatcher)
  val baseUrl = "http://localhost:8080/".toHttpUrl()
  val loader = ZiplineLoader(
    dispatcher = dispatcher,
    httpClient = OkHttpZiplineHttpClient(baseUrl, OkHttpClient()),
    embeddedDir = "/".toPath(),
    embeddedFileSystem = FileSystem.RESOURCES,
    cacheDbDriver = DriverFactory().createDriver(),
    cacheDir = "zipline-cache".toPath(),
    cacheFileSystem = FileSystem.SYSTEM,
    cacheMaxSizeInBytes = 10 * 1024 * 1024,
    nowMs = System::currentTimeMillis,
  )
  loader.load(zipline, baseUrl.resolve("/manifest.zipline.json").toString())
  val moduleName = "./zipline-root-trivia-js.js"
  zipline.quickJs.evaluate(
    "require('$moduleName').app.cash.zipline.samples.trivia.launchZipline()",
    "launchZiplineJvm.kt"
  )
  return zipline
}
