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

import app.cash.zipline.ZiplineManifest
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.assertDownloadedToEmbeddedManifest
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Before
import org.junit.Test

class ZiplineGradleDownloaderTest {
  private val testFixtures = LoaderTestFixtures()
  private val webServer = MockWebServer()
  private val ziplineDownloader = ZiplineGradleDownloader()
  private val rootProject = File("src/test/resources/downloaderTest")
  private val downloadDir = File("$rootProject/resources/downloaderTest")

  @Before
  fun setUp() {
    rootProject.deleteRecursively()
  }

  @After
  fun tearDown() {
    rootProject.deleteRecursively()
  }

  @Test
  fun `integration test to load from mock url`() {
    // Zipline files
    val manifest = ZiplineManifest.create(
      modules = mapOf(
        "id" to ZiplineManifest.Module(
          url = webServer.url("/latest/app/alpha.zipline").toString(),
          sha256 = testFixtures.alphaSha256,
          dependsOnIds = listOf(),
        ),
      ),
      mainFunction = "zipline.ziplineMain",
    )
    val manifestJsonString = manifest.encodeJson()

    // Enqueue the manifest
    webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(manifestJsonString),
    )

    // Enqueue the zipline file
    webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().write(testFixtures.alphaByteString)),
    )

    val applicationName = "app1"
    val manifestUrl = webServer.url("/latest/app/manifest.zipline.json").toString()

    ziplineDownloader.download(downloadDir, applicationName, manifestUrl)

    // Confirm files successfully downloaded
    val fileSystem = FileSystem.SYSTEM
    val downloadDirPath = downloadDir.toOkioPath()
    assertTrue(fileSystem.exists(downloadDirPath / getApplicationManifestFileName(applicationName)))

    val actualManifestByteString = fileSystem.read(
      downloadDirPath / getApplicationManifestFileName(applicationName),
    ) { readByteString() }
    assertDownloadedToEmbeddedManifest(
      manifest.copy(baseUrl = manifestUrl),
      actualManifestByteString,
    )
    assertTrue(fileSystem.exists(downloadDirPath / testFixtures.alphaSha256Hex))
    assertEquals(
      testFixtures.alphaByteString,
      fileSystem.read(downloadDirPath / testFixtures.alphaSha256Hex) { readByteString() },
    )
  }

  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline-loader internals.
  private fun getApplicationManifestFileName(applicationName: String) =
    app.cash.zipline.loader.internal.getApplicationManifestFileName(applicationName)
}
