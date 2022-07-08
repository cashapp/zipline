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
package app.cash.zipline.tests

import app.cash.zipline.Zipline
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

suspend fun launchZipline(dispatcher: CoroutineDispatcher): Zipline {
  // A fake HTTP client that returns files as the Webpack dev server would return them.
  val localDirectoryHttpClient = object : ZiplineHttpClient {
    val base = "build/compileSync/main/productionExecutable/kotlinZipline".toPath()
    override suspend fun download(url: String): ByteString {
      val file = url.substringAfterLast("/")
      return FileSystem.SYSTEM.read(base / file) { readByteString() }
    }

    override fun resolve(baseUrl: String, link: String) = "http://localhost:99999/$link"
  }
  val loader = ZiplineLoader(dispatcher, localDirectoryHttpClient)

  val zipline = loader.loadOrFail("test", "http://localhost:99999/manifest.zipline.json")

  return zipline
}

fun main() {
  val executorService = Executors.newFixedThreadPool(1) {
    Thread(it, "Zipline")
  }
  val dispatcher = executorService.asCoroutineDispatcher()
  runBlocking(dispatcher) {
    val zipline = launchZipline(dispatcher)
    val greetService = zipline.take<GreetService>("greetService")
    val greeting = greetService.greet("Jesse")
    println("end-to-end call result: '$greeting'")
  }
  exitProcess(0)
}
