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
package app.cash.zipline.loader.fetcher

import app.cash.zipline.loader.ZiplineLoader.Companion.getApplicationManifestFileName
import app.cash.zipline.loader.ZiplineManifest
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Fetch from embedded fileSystem that ships with the app.
 */
class FsEmbeddedFetcher(
  private val embeddedDir: Path,
  private val embeddedFileSystem: FileSystem,
) : Fetcher {
  override suspend fun fetch(
    applicationId: String,
    sha256: ByteString,
    url: String,
    manifestForApplicationId: String?
  ): ByteString? {
    val applicationManifestFileName: String? = getApplicationManifestFileName(manifestForApplicationId)
    val filePath = embeddedDir / (applicationManifestFileName ?: sha256.hex())
    return when {
      embeddedFileSystem.exists(filePath) -> {
        embeddedFileSystem.read(filePath) {
          readByteString()
        }
      }
      else -> {
        null
      }
    }
  }

  override suspend fun pin(
    applicationId: String,
    manifest: ZiplineManifest
  ): Boolean = false
}
