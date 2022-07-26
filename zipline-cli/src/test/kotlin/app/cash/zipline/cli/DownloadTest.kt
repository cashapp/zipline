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

import app.cash.zipline.loader.ZiplineLoader.Companion.getApplicationManifestFileName
import app.cash.zipline.loader.ZiplineManifest
import app.cash.zipline.loader.ZiplineModule
import app.cash.zipline.loader.testing.LoaderTestFixtures
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.FileSystem
import org.junit.Test
import picocli.CommandLine
import picocli.CommandLine.MissingParameterException

class DownloadTest {
  private val TMP_DIR_PATH = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "zipline-download"

  private val webServer = MockWebServer()
  private val testFixtures = LoaderTestFixtures()
  private val fileSystem = FileSystem.SYSTEM

  @Test fun downloadWithParameters() {
    fromArgs("-A", "app1", "-D", TMP_DIR_PATH.toString(), "-M", "test.cash.app")
  }

  @Test fun downloadMissingApplicationName() {
    val exception = assertFailsWith<MissingParameterException> {
      fromArgs("-D", TMP_DIR_PATH.toString(), "-M", "test.cash.app")
    }
    assertEquals("Missing required option: '--application-name=<applicationName>'", exception.message)
  }

  @Test fun downloadMissingManifestUrl() {
    val exception = assertFailsWith<MissingParameterException> {
      fromArgs("-A", "app1", "-D", TMP_DIR_PATH.toString())
    }
    assertEquals("Missing required option: '--manifest-url=<manifestUrl>'", exception.message)
  }

  @Test fun downloadMissingDownloadDir() {
    val exception = assertFailsWith<MissingParameterException> {
      fromArgs("-A", "app1", "-M", "test.cash.app")
    }
    assertEquals("Missing required option: '--download-dir=<downloadDir>'", exception.message)
  }

  @Test fun downloadFromMockWebServer() {
    // Wipe and re-create local test download directory
    fileSystem.deleteRecursively(TMP_DIR_PATH)
    fileSystem.createDirectories(TMP_DIR_PATH)
    assertEquals(0, fileSystem.list(TMP_DIR_PATH).size)

    // Seed mock web server with zipline manifest and files
    // Zipline files
    val applicationName = "app1"
    val manifest = ZiplineManifest.create(
      modules = mapOf(
        "id" to ZiplineModule(
          url = webServer.url("/latest/app/alpha.zipline").toString(),
          sha256 = testFixtures.alphaSha256,
          dependsOnIds = listOf(),
          patchFrom = null,
          patchUrl = null,
        )
      ),
      mainFunction = "zipline.ziplineMain()"
    )
    val manifestJsonString = Json.encodeToString(manifest)

    // Enqueue the manifest
    webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(manifestJsonString)
    )

    // Enqueue the zipline file
    webServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(Buffer().write(testFixtures.alphaByteString))
    )

    val manifestUrl = webServer.url("/latest/app/manifest.zipline.json").toString()

    // Download using the CLI
    CommandLine(Download()).execute("-A", applicationName, "-D", TMP_DIR_PATH.toString(), "-M", manifestUrl)

    // Check that files were downloaded
    assertTrue(fileSystem.exists(TMP_DIR_PATH))
    assertTrue(fileSystem.exists(TMP_DIR_PATH / getApplicationManifestFileName(applicationName)))
    assertTrue(fileSystem.exists(TMP_DIR_PATH / testFixtures.alphaSha256Hex))
  }

  companion object {
    fun fromArgs(vararg args: String?): Download {
      return CommandLine.populateCommand(Download(), *args)
    }
  }
}
