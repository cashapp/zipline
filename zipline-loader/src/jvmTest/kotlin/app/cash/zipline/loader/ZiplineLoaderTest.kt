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
package app.cash.zipline.loader

import app.cash.zipline.QuickJs
import app.cash.zipline.Zipline
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class ZiplineLoaderTest {
  private val httpClient = FakeZiplineHttpClient()
  private val dispatcher = TestCoroutineDispatcher()
  private val loader = ZiplineLoader(
    httpClient = httpClient,
    dispatcher = dispatcher
  )
  private lateinit var quickJs: QuickJs

  @BeforeTest
  fun setUp() {
    quickJs = QuickJs.create()
  }

  @AfterTest
  fun tearDown() {
    quickJs.close()
  }

  @Test
  fun `happy path`() {
    httpClient.filePathToByteString = mapOf(
      alphaFilePath to alphaBytecode(quickJs),
      bravoFilePath to bravoBytecode(quickJs)
    )
    val zipline = Zipline.create(dispatcher)
    dispatcher.runBlockingTest {
      loader.load(zipline, manifest(quickJs))
    }
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
  }

  @Test
  fun `load manifest from url`() {
    httpClient.filePathToByteString = mapOf(
      manifestPath to Json.encodeToString(manifest(quickJs)).encodeUtf8(),
      alphaFilePath to alphaBytecode(quickJs),
      bravoFilePath to bravoBytecode(quickJs)
    )
    val zipline = Zipline.create(dispatcher)
    dispatcher.runBlockingTest {
      loader.load(zipline, manifestPath)
    }
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
  }

  companion object {
    private val alphaJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "alpha loaded\n"
      |""".trimMargin()
    private fun alphaBytecode(quickJs: QuickJs) = ziplineFile(quickJs, alphaJs, "alpha.js")
    private const val alphaFilePath = "/alpha.zipline"

    private val bravoJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "bravo loaded\n"
      |""".trimMargin()
    private fun bravoBytecode(quickJs: QuickJs) = ziplineFile(quickJs, bravoJs, "bravo.js")
    private const val bravoFilePath = "/bravo.zipline"

    private const val manifestPath = "/manifest.zipline.json"
    private fun manifest(quickJs: QuickJs) = ZiplineManifest.create(
      modules = mapOf(
        "bravo" to ZiplineModule(
          url = bravoFilePath,
          sha256 = bravoBytecode(quickJs).sha256(),
          dependsOnIds = listOf("alpha"),
        ),
        "alpha" to ZiplineModule(
          url = alphaFilePath,
          sha256 = alphaBytecode(quickJs).sha256(),
          dependsOnIds = listOf(),
        ),
      )
    )

    private fun ziplineFile(quickJs: QuickJs, javaScript: String, fileName: String): ByteString {
      val ziplineFile = ZiplineFile(
        CURRENT_ZIPLINE_VERSION,
        quickJs.compile(javaScript, fileName).toByteString()
      )

      val buffer = Buffer()
      ziplineFile.writeTo(buffer)
      return buffer.readByteString()
    }
  }
}
