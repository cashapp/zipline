/*
 * Copyright (C) 2022 Square, Inc.
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
package app.cash.zipline.cli

import app.cash.zipline.ZiplineManifest
import app.cash.zipline.loader.testing.LoaderTestFixtures
import com.github.ajalt.clikt.core.MissingOption
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.FileSystem
import org.junit.Test

class DownloadTest {
  private val tmpDirPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "zipline-download"

  private val webServer = MockWebServer()
  private val testFixtures = LoaderTestFixtures()
  private val fileSystem = FileSystem.SYSTEM

  @Test fun downloadMissingApplicationName() {
    val exception = assertFailsWith<MissingOption> {
      runCommand("-D", tmpDirPath.toString(), "-M", "test.cash.app")
    }
    assertEquals("--application-name", exception.paramName)
  }

  @Test fun downloadMissingManifestUrl() {
    val exception = assertFailsWith<MissingOption> {
      runCommand("-A", "app1", "-D", tmpDirPath.toString())
    }
    assertEquals("--manifest-url", exception.paramName)
  }

  @Test fun downloadMissingDownloadDir() {
    val exception = assertFailsWith<MissingOption> {
      runCommand("-A", "app1", "-M", "test.cash.app")
    }
    assertEquals("--download-dir", exception.paramName)
  }

  @Test fun downloadFromMockWebServer() {
    // Wipe and re-create local test download directory
    fileSystem.deleteRecursively(tmpDirPath)
    fileSystem.createDirectories(tmpDirPath)
    assertEquals(0, fileSystem.list(tmpDirPath).size)

    // Seed mock web server with zipline manifest and files
    // Zipline files
    val applicationName = "app1"
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

    val manifestUrl = webServer.url("/latest/app/manifest.zipline.json").toString()

    // Download using the CLI
    runCommand("-A", applicationName, "-D", tmpDirPath.toString(), "-M", manifestUrl)

    // Check that files were downloaded
    assertTrue(fileSystem.exists(tmpDirPath))
    assertTrue(fileSystem.exists(tmpDirPath / getApplicationManifestFileName(applicationName)))
    assertTrue(fileSystem.exists(tmpDirPath / testFixtures.alphaSha256Hex))
  }

  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline-loader internals.
  private fun getApplicationManifestFileName(applicationName: String) =
    app.cash.zipline.loader.internal.getApplicationManifestFileName(applicationName)

  companion object {
    private fun runCommand(vararg s: String) {
      val command = Download()
      command.parse(s.toList())
    }
  }
}
