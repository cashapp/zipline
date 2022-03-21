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

import app.cash.zipline.QuickJs
import app.cash.zipline.loader.ZiplineLoader
import app.cash.zipline.loader.ZiplineManifest
import app.cash.zipline.loader.ZiplineModule
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Before
import org.junit.Test

class ZiplineDownloaderTest {
  private lateinit var quickJs: QuickJs
  private val webServer = MockWebServer()
  private val ziplineDownloader = ZiplineDownloader()

  private val rootProject = File("src/test/resources/downloaderTest")
  private val downloadDir = File("$rootProject/resources/downloaderTest")

  @Before
  fun setUp() {
    quickJs = QuickJs.create()
    rootProject.deleteRecursively()
  }

  @After
  fun tearDown() {
    quickJs.close()
    rootProject.deleteRecursively()
  }

  @Test
  fun `integration test to load from mock url`() {
    // Zipline files
    val alphaByteString = alphaBytecode(quickJs)
    val manifest = ZiplineManifest.create(
      modules = mapOf(
        "id" to ZiplineModule(
          url = webServer.url("/latest/app/alpha.zipline").toString(),
          sha256 = alphaByteString.sha256(),
          dependsOnIds = listOf(),
          patchFrom = null,
          patchUrl = null,
        )
      )
    )
    val manifestJsonString = Json.encodeToString(manifest)

    // Enqueue the manifest
    webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(manifestJsonString)
        .addHeader("Server", "treehouse-server")
    )

    // Enqueue the zipline file
    webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().write(alphaByteString))
        .addHeader("Server", "treehouse-server")
    )

    val manifestUrl = webServer.url("/latest/app/manifest.zipline.json").toString()

    ziplineDownloader.download(manifestUrl, downloadDir)

    // Confirm files successfully downloaded
    val fileSystem = FileSystem.SYSTEM
    val downloadDirPath = downloadDir.toOkioPath()
    assertTrue(fileSystem.exists(downloadDirPath / ZiplineLoader.PREBUILT_MANIFEST_FILE_NAME))
    assertEquals(manifestJsonString.encodeUtf8(), fileSystem.read(downloadDirPath / ZiplineLoader.PREBUILT_MANIFEST_FILE_NAME) { readByteString() })
    assertTrue(fileSystem.exists(downloadDirPath / alphaBytecode(quickJs).sha256().hex()))
    assertEquals(alphaBytecode(quickJs), fileSystem.read(downloadDirPath / alphaBytecode(quickJs).sha256().hex()) { readByteString() })
  }
}
