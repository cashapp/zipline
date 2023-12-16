/*
 * Copyright (C) 2023 Cash App
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
package app.cash.zipline.tests

import app.cash.zipline.Zipline
import app.cash.zipline.ZiplineException
import app.cash.zipline.loader.DefaultFreshnessCheckerNotFresh
import app.cash.zipline.loader.LoadResult
import app.cash.zipline.loader.ManifestVerifier.Companion.NO_SIGNATURE_CHECKS
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.ZiplineLoader
import java.util.concurrent.Executors
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okio.ByteString
import okio.FileSystem
import okio.Path.Companion.toPath

suspend fun launchZipline(
  optimizeMode: String,
  dispatcher: CoroutineDispatcher,
): Zipline {
  // A fake HTTP client that returns files as the Webpack dev server would return them.
  val localDirectoryHttpClient = object : ZiplineHttpClient() {
    val base = when (optimizeMode) {
      "development" -> "build/compileSync/js/main/developmentExecutable/kotlinZipline".toPath()
      else -> "build/dist/js/productionExecutableZipline".toPath()
    }
    override suspend fun download(
      url: String,
      requestHeaders: List<Pair<String, String>>,
    ): ByteString {
      val file = url.substringAfterLast("/")
      return FileSystem.SYSTEM.read(base / file) { readByteString() }
    }
  }
  val loader = ZiplineLoader(
    dispatcher = dispatcher,
    manifestVerifier = NO_SIGNATURE_CHECKS,
    httpClient = localDirectoryHttpClient,
  )

  val loadResult = loader.loadOnce(
    "test",
    DefaultFreshnessCheckerNotFresh,
    "https://localhost/manifest.zipline.json",
  ) as LoadResult.Success

  return loadResult.zipline
}

fun main(vararg args: String) {
  val executorService = Executors.newFixedThreadPool(1) {
    Thread(it, "Zipline")
  }
  val optimizeMode = args.single()
  val dispatcher = executorService.asCoroutineDispatcher()
  runBlocking(dispatcher) {
    val zipline = launchZipline(optimizeMode, dispatcher)
    val crashService = zipline.take<CrashService>("crashService")
    try {
      crashService.crash()
    } catch (e: ZiplineException) {
      e.printStackTrace()
    }
  }
  exitProcess(0)
}
