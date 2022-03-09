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
package app.cash.zipline.loader.strategy

import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
import app.cash.zipline.loader.ZiplineHttpClient
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Used for the Zipline Gradle Plugin task to download all Zipline files for shipping within
 * embedded filesystem during an app release.
 */
class DownloadOnlyStrategy(
  private val httpClient: ZiplineHttpClient,
  private val concurrentDownloadsSemaphore: Semaphore,
  private val fileSystem: FileSystem,
  private val downloadDirectory: Path,
): LoadStrategy {
  override suspend fun getZiplineFile(id: String, sha256: ByteString, url: String): ZiplineFile {
    val ziplineFileBytes = concurrentDownloadsSemaphore.withPermit {
      httpClient.download(url)
    }
    return ziplineFileBytes.toZiplineFile()
  }

  override suspend fun processFile(
    ziplineFile: ZiplineFile,
    id: String,
    sha256: ByteString
  ) {
    val filePath = downloadDirectory / sha256.hex()
    fileSystem.createDirectories(downloadDirectory)
    fileSystem.write(filePath) {
      write(ziplineFile.toByteString())
    }
  }
}
