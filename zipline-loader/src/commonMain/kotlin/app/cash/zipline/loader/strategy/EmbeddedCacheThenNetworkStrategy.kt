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

import app.cash.zipline.Zipline
import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.multiplatformLoadJsModule
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Default strategy which gets modules from embedded filesystem (ships with app), cache,
 * or by network; followed by saving to cache and loading into a Zipline instance
 *
 */
class EmbeddedCacheThenNetworkStrategy(
  private val zipline: Zipline,
  private val httpClient: ZiplineHttpClient,
  private val concurrentDownloadsSemaphore: Semaphore,
  private val embeddedFileSystem: FileSystem,
  private val embeddedDirectory: Path,
  private val cache: ZiplineCache,
): LoadStrategy {
  override suspend fun getZiplineFile(id: String, sha256: ByteString, url: String): ZiplineFile {
    val resourcePath = embeddedDirectory / sha256.hex()
    val ziplineFileBytes = if (embeddedFileSystem.exists(resourcePath)) {
      embeddedFileSystem.read(resourcePath) {
        readByteString()
      }
    } else {
      cache.getOrPut(sha256) {
        concurrentDownloadsSemaphore.withPermit {
          httpClient.download(url)
        }
      }
    }
    return ziplineFileBytes.toZiplineFile()
  }

  override suspend fun processFile(
    ziplineFile: ZiplineFile,
    id: String,
    sha256: ByteString
  ) {
    zipline.multiplatformLoadJsModule(ziplineFile.quickjsBytecode.toByteArray(), id)
  }
}
