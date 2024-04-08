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

import app.cash.zipline.loader.ManifestVerifier.Companion.NO_SIGNATURE_CHECKS
import app.cash.zipline.loader.ZiplineLoader
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class Download : CliktCommand(NAME) {
  private val applicationName by option("-A", "--application-name")
    .required()
    .help("Application name for the Zipline Manifest.")

  private val manifestUrl by option("-M", "--manifest-url")
    .required()
    .help("URL to the Zipline Manifest for the code to download.")

  private val downloadDir by option("-D", "--download-dir")
    .path(canBeFile = false)
    .required()
    .help("Directory where code will be downloaded to.")

  private val executorService = Executors.newSingleThreadExecutor { Thread(it, "Zipline") }
  private val dispatcher = executorService.asCoroutineDispatcher()
  private val client = OkHttpClient()

  private fun download(applicationName: String, manifestUrl: String, downloadDir: File) {
    println("Zipline Download [manifestUrl=$manifestUrl][downloadDir=$downloadDir]...")
    val ziplineLoader = ZiplineLoader(
      dispatcher = dispatcher,
      manifestVerifier = NO_SIGNATURE_CHECKS,
      httpClient = client,
    )
    runBlocking {
      ziplineLoader.download(
        applicationName = applicationName,
        downloadFileSystem = FileSystem.SYSTEM,
        downloadDir = downloadDir.toOkioPath(),
        manifestUrl = manifestUrl,
      )
    }
  }

  override fun run() {
    download(
      applicationName = applicationName,
      manifestUrl = manifestUrl,
      downloadDir = downloadDir.toFile(),
    )
  }

  companion object {
    const val NAME = "download"
  }
}
