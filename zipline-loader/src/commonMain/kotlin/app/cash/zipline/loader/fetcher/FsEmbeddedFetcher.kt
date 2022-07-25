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

import app.cash.zipline.EventListener
import app.cash.zipline.loader.ZiplineLoader.Companion.getApplicationManifestFileName
import app.cash.zipline.loader.ZiplineManifest
import okio.ByteString
import okio.FileSystem
import okio.Path
/**
 * Fetch from embedded fileSystem that ships with the app.
 */
internal class FsEmbeddedFetcher(
  private val embeddedDir: Path,
  private val embeddedFileSystem: FileSystem,
  private val eventListener: EventListener,
) : Fetcher {
  override suspend fun fetch(
    applicationName: String,
    id: String,
    sha256: ByteString,
    url: String,
  ): ByteString? = fetchByteString(embeddedDir / sha256.hex())

  override suspend fun fetchManifest(
    applicationName: String,
    id: String,
    url: String?,
  ): LoadedManifest? {
    val manifestBytes = fetchByteString(
      embeddedDir / getApplicationManifestFileName(applicationName)
    ) ?: return null
    return LoadedManifest(manifestBytes)
  }

  override suspend fun pin(
    applicationName: String,
    loadedManifest: LoadedManifest,
  ) {
  }

  override suspend fun unpin(
    applicationName: String,
    loadedManifest: LoadedManifest,
  ) {
  }

  private fun fetchByteString(filePath: Path) = when {
    embeddedFileSystem.exists(filePath) -> {
      embeddedFileSystem.read(filePath) {
        readByteString()
      }
    }
    else -> null
  }
}

