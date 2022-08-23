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

import app.cash.zipline.ApplicationLoadReportEventListener
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.alphaUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.bravoUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.manifestUrl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import okio.FileSystem

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class ApplicationLoadReportEventListenerTest {
  private val embeddedFileSystem = systemFileSystem
  private val embeddedDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
  private val eventListener = ApplicationLoadReportEventListener(embeddedFileSystem, embeddedDir)
  private val tester = LoaderTester(
    eventListener = eventListener
  )

  private val testFixtures = LoaderTestFixtures()

  private lateinit var loader: ZiplineLoader
  private lateinit var httpClient: FakeZiplineHttpClient

  @BeforeTest
  fun setUp() {
    tester.beforeTest()
    loader = tester.loader
    httpClient = tester.httpClient
  }

  @AfterTest
  fun tearDown() {
    tester.afterTest()
  }

  @Test
  fun test(): Unit = runBlocking {
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestNoBaseUrlByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    val applicationName = "test"
    val zipline = loader.loadOnce(applicationName, manifestUrl).zipline
    zipline.close()

    val metrics = embeddedFileSystem.read(embeddedDir / "$applicationName.txt") {
      readUtf8()
    }
    assertEqualsIgnoringTimes(
      """
      |  65ms - Zipline application test loaded
      |    27ms - Module alpha loaded
      |       0ms - Waiting to acquire fetch permit
      |       0ms - Attempted to fetch using FsEmbeddedFetcher but could not resolve
      |      23ms - Fetched using FsCachingFetcher
      |       0ms - Waiting on upstream modules to load
      |       1ms - Loaded into QuickJS
      |     5ms - Module bravo loaded
      |       0ms - Waiting to acquire fetch permit
      |       0ms - Attempted to fetch using FsEmbeddedFetcher but could not resolve
      |       4ms - Fetched using FsCachingFetcher
      |       0ms - Waiting on upstream modules to load
      |       0ms - Loaded into QuickJS
      """.trimMargin(),
      metrics,
    )
  }

  private fun assertEqualsIgnoringTimes(expected: String, actual: String) {
    val regex = Regex("\\d+ms")
    assertEquals(expected.replace(regex, ""), actual.replace(regex, ""))
  }

}
