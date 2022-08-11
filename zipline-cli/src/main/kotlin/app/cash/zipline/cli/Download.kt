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

import app.cash.zipline.cli.Download.Companion.NAME
import app.cash.zipline.loader.ZiplineLoader
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
  name = NAME, description = ["Recursively download Zipline code to a directory from a URL"],
  mixinStandardHelpOptions = true, versionProvider = Main.VersionProvider::class
)
class Download : Runnable {
  @Option(
    names = ["-A", "--application-name"],
    description = ["Application name for the Zipline Manifest."], required = true
  )
  lateinit var applicationName: String

  @Option(
    names = ["-M", "--manifest-url"],
    description = ["URL to the Zipline Manifest for the code to download."], required = true
  )
  lateinit var manifestUrl: String

  @Option(
    names = ["-D", "--download-dir"], description = ["Directory where code will be downloaded to."],
    required = true
  )
  lateinit var downloadDir: File

  private val executorService = Executors.newSingleThreadExecutor { Thread(it, "Zipline") }
  private val dispatcher = executorService.asCoroutineDispatcher()
  private val client = OkHttpClient()

  private fun download(applicationName: String, manifestUrl: String, downloadDir: File) {
    println("Zipline Download [manifestUrl=$manifestUrl][downloadDir=$downloadDir]...")
    val ziplineLoader = ZiplineLoader(
      dispatcher = dispatcher,
      httpClient = client,
    )
    runBlocking {
      ziplineLoader.download(
        applicationName = applicationName,
        downloadDir = downloadDir.toOkioPath(),
        downloadFileSystem = FileSystem.SYSTEM,
        manifestUrl = manifestUrl,
      )
    }
  }

  override fun run() {
    download(applicationName = applicationName, manifestUrl = manifestUrl, downloadDir = downloadDir)
  }

  companion object {
    const val NAME = "download"
  }
}
