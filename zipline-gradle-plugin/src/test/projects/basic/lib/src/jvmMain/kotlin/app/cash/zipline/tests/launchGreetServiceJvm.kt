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
import app.cash.zipline.loader.OkHttpZiplineHttpClient
import app.cash.zipline.loader.ZiplineLoader
import app.cash.zipline.loader.fetcher.HttpFetcher
import java.util.concurrent.Executors
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

suspend fun launchZipline(dispatcher: CoroutineDispatcher): Zipline {
  val zipline = Zipline.create(dispatcher)
  val manifestUrl = "http://localhost:8080/manifest.zipline.json"
  val httpClient = OkHttpZiplineHttpClient(OkHttpClient())
  val loader = ZiplineLoader(
    dispatcher = dispatcher,
    httpClient = httpClient,
    fetchers = listOf(HttpFetcher(httpClient = httpClient)),
  )
  loader.load(zipline, manifestUrl)
  val moduleName = "./zipline-root-trivia-js.js"
  zipline.quickJs.evaluate(
    "require('$moduleName').app.cash.zipline.tests.launchGreetService()",
    "launchGreetServiceJvm.kt"
  )
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
    println(greetService.greet("Jesse"))
  }
  exitProcess(0)
}
