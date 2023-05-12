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
package app.cash.zipline.loader

import app.cash.zipline.testing.LoggingEventListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okio.IOException

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class LoaderEventsTest {
  private val eventListener = LoggingEventListener()

  private val tester = LoaderTester(eventListener)

  @BeforeTest
  fun setUp() {
    tester.beforeTest()
  }

  @AfterTest
  fun tearDown() {
    tester.afterTest()
  }

  @Test
  fun happyPathEvents() = runBlocking {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals(
      listOf(
        "applicationLoadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadEnd red https://example.com/files/red/red.manifest.zipline.json",
        "ziplineCreated",
        "downloadStart red https://example.com/files/red/apple.zipline",
        "downloadEnd red https://example.com/files/red/apple.zipline",
        "moduleLoadStart apple",
        "moduleLoadEnd apple",
        "initializerStart red",
        "initializerEnd red",
        "mainFunctionStart red",
        "mainFunctionEnd red",
        "applicationLoadSuccess red https://example.com/files/red/red.manifest.zipline.json",
      ),
      eventListener.takeAll(skipServiceEvents = true),
    )
  }

  @Test
  fun loadSkippedIfManifestHasNotChanged() = runBlocking {
    val flow = tester.load("red", "apple", count = 2)
    assertEquals(1, flow.toList().size)
    assertEquals(
      listOf(
        "applicationLoadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadEnd red https://example.com/files/red/red.manifest.zipline.json",
        "ziplineCreated",
        "downloadStart red https://example.com/files/red/apple.zipline",
        "downloadEnd red https://example.com/files/red/apple.zipline",
        "moduleLoadStart apple",
        "moduleLoadEnd apple",
        "initializerStart red",
        "initializerEnd red",
        "mainFunctionStart red",
        "mainFunctionEnd red",
        "applicationLoadSuccess red https://example.com/files/red/red.manifest.zipline.json",
        "applicationLoadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadEnd red https://example.com/files/red/red.manifest.zipline.json",
        "applicationLoadSkipped red https://example.com/files/red/red.manifest.zipline.json",
      ),
      eventListener.takeAll(skipServiceEvents = true),
    )
  }

  @Test
  fun loadAlreadyCached() = runBlocking {
    assertEquals("apple", tester.success("red", "apple"))
    eventListener.takeAll()

    // On the 2nd load, the manifest is fetched again but the module is not.
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals(
      listOf(
        "applicationLoadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadEnd red https://example.com/files/red/red.manifest.zipline.json",
        "ziplineCreated",
        "moduleLoadStart apple",
        "moduleLoadEnd apple",
        "initializerStart red",
        "initializerEnd red",
        "mainFunctionStart red",
        "mainFunctionEnd red",
        "applicationLoadSuccess red https://example.com/files/red/red.manifest.zipline.json",
      ),
      eventListener.takeAll(skipServiceEvents = true),
    )
  }

  @Test
  fun manifestDownloadFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureManifestFetchFails("red"))
    assertEquals(
      listOf(
        "applicationLoadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadFailed red https://example.com/files/red/red.manifest.zipline.json " +
          "${IOException::class.qualifiedName}: 404: https://example.com/files/red/red.manifest.zipline.json not found",
        "applicationLoadFailed red " +
          "${IOException::class.qualifiedName}: 404: https://example.com/files/red/red.manifest.zipline.json not found",
        "applicationLoadStart red null",
        "ziplineCreated",
        "moduleLoadStart firetruck",
        "moduleLoadEnd firetruck",
        "initializerStart red",
        "initializerEnd red",
        "mainFunctionStart red",
        "mainFunctionEnd red",
        "applicationLoadSuccess red null",
      ),
      eventListener.takeAll(skipServiceEvents = true),
    )
  }

  @Test
  fun codeDownloadFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeFetchFails("red"))
    assertEquals(
      listOf(
        "applicationLoadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadEnd red https://example.com/files/red/red.manifest.zipline.json",
        "ziplineCreated",
        "downloadStart red https://example.com/files/red/unreachable.zipline",
        "downloadFailed red https://example.com/files/red/unreachable.zipline " +
          "${IOException::class.qualifiedName}: 404: https://example.com/files/red/unreachable.zipline not found",
        "ziplineClosed",
        "applicationLoadFailed red " +
          "${IOException::class.qualifiedName}: 404: https://example.com/files/red/unreachable.zipline not found",
        "applicationLoadStart red null",
        "ziplineCreated",
        "moduleLoadStart firetruck",
        "moduleLoadEnd firetruck",
        "initializerStart red",
        "initializerEnd red",
        "mainFunctionStart red",
        "mainFunctionEnd red",
        "applicationLoadSuccess red null",
      ),
      eventListener.takeAll(skipServiceEvents = true),
    )
  }

  @Test
  fun codeLoadFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeLoadFails("red"))
    assertEquals(
      listOf(
        "applicationLoadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadEnd red https://example.com/files/red/red.manifest.zipline.json",
        "ziplineCreated",
        "downloadStart red https://example.com/files/red/broken.zipline",
        "downloadEnd red https://example.com/files/red/broken.zipline",
        "moduleLoadStart broken",
        "moduleLoadEnd broken",
        "ziplineClosed",
        "applicationLoadFailed red app.cash.zipline.QuickJsException: broken",
        "applicationLoadStart red null",
        "ziplineCreated",
        "moduleLoadStart firetruck",
        "moduleLoadEnd firetruck",
        "initializerStart red",
        "initializerEnd red",
        "mainFunctionStart red",
        "mainFunctionEnd red",
        "applicationLoadSuccess red null",
      ),
      eventListener.takeAll(skipServiceEvents = true),
    )
  }

  @Test
  fun codeRunFails() = runBlocking {
    tester.seedEmbedded("red", "firetruck")
    assertEquals("firetruck", tester.failureCodeRunFails("red"))
    assertEquals(
      listOf(
        "applicationLoadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadEnd red https://example.com/files/red/red.manifest.zipline.json",
        "ziplineCreated",
        "downloadStart red https://example.com/files/red/crashes.zipline",
        "downloadEnd red https://example.com/files/red/crashes.zipline",
        "moduleLoadStart crashes",
        "moduleLoadEnd crashes",
        "initializerStart red",
        "initializerEnd red",
        "ziplineClosed",
        "applicationLoadFailed red ${IllegalArgumentException::class.qualifiedName}: Zipline code run failed",
        "applicationLoadStart red null",
        "ziplineCreated",
        "moduleLoadStart firetruck",
        "moduleLoadEnd firetruck",
        "initializerStart red",
        "initializerEnd red",
        "mainFunctionStart red",
        "mainFunctionEnd red",
        "applicationLoadSuccess red null",
      ),
      eventListener.takeAll(skipServiceEvents = true),
    )
  }
}
