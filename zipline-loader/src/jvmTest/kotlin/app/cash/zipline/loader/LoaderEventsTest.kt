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
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class LoaderEventsTest {
  private val eventListener = LoggingEventListener()
  private val tester = LoaderTester(eventListener)

  @Test
  fun happyPathEvents() = runBlocking {
    assertEquals("apple", tester.success("red", "apple"))
    assertEquals(
      listOf(
        "applicationLoadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadStart red https://example.com/files/red/red.manifest.zipline.json",
        "downloadEnd red https://example.com/files/red/red.manifest.zipline.json",
        "downloadStart red https://example.com/files/red/apple.zipline",
        "downloadEnd red https://example.com/files/red/apple.zipline",
        "applicationLoadEnd red https://example.com/files/red/red.manifest.zipline.json",
      ),
      eventListener.takeAll(skipServiceEvents = true)
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
        "applicationLoadEnd red https://example.com/files/red/red.manifest.zipline.json",
      ),
      eventListener.takeAll(skipServiceEvents = true)
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
          "java.io.IOException: 404: https://example.com/files/red/red.manifest.zipline.json not found",
        "applicationLoadFailed red " +
          "java.io.IOException: 404: https://example.com/files/red/red.manifest.zipline.json not found",
        "applicationLoadStart red null",
        "applicationLoadEnd red null",
      ),
      eventListener.takeAll(skipServiceEvents = true)
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
        "downloadStart red https://example.com/files/red/unreachable.zipline",
        "downloadFailed red https://example.com/files/red/unreachable.zipline " +
          "java.io.IOException: 404: https://example.com/files/red/unreachable.zipline not found",
        "applicationLoadFailed red " +
          "java.io.IOException: 404: https://example.com/files/red/unreachable.zipline not found",
        "applicationLoadStart red null",
        "applicationLoadEnd red null",
      ),
      eventListener.takeAll(skipServiceEvents = true)
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
        "downloadStart red https://example.com/files/red/broken.zipline",
        "downloadEnd red https://example.com/files/red/broken.zipline",
        "applicationLoadFailed red app.cash.zipline.QuickJsException: broken",
        "applicationLoadStart red null",
        "applicationLoadEnd red null",
      ),
      eventListener.takeAll(skipServiceEvents = true)
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
        "downloadStart red https://example.com/files/red/crashes.zipline",
        "downloadEnd red https://example.com/files/red/crashes.zipline",
        "applicationLoadFailed red java.lang.IllegalArgumentException: Zipline code run failed",
        "applicationLoadStart red null",
        "applicationLoadEnd red null",
      ),
      eventListener.takeAll(skipServiceEvents = true)
    )
  }
}
