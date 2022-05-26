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

import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.ZiplineManifest
import okio.ByteString

/**
 * Fetch from the network and save to local fileSystem cache once downloaded.
 */
class FsCachingFetcher(
  private val cache: ZiplineCache,
  private val delegate: Fetcher,
) : Fetcher {
  override suspend fun fetch(
    applicationName: String,
    id: String,
    sha256: ByteString,
    url: String,
  ): ByteString? {
    return cache.getOrPut(applicationName, sha256) {
      delegate.fetch(applicationName, id, sha256, url)
    }
  }

  override suspend fun fetchManifest(
    applicationName: String,
    id: String,
    url: String?,
  ): ZiplineManifest? {
    // Prefer the network for the freshest manifest. Fallback to the cache if that fails.
    return try {
      delegate.fetchManifest(applicationName, id, url)
        ?: cache.getPinnedManifest(applicationName)
    } catch (e: Exception) {
      cache.getPinnedManifest(applicationName) ?: throw e
    }
  }

  override suspend fun pin(applicationName: String, manifest: ZiplineManifest) =
    cache.pinManifest(applicationName, manifest)

  override suspend fun unpin(applicationName: String, manifest: ZiplineManifest) =
    cache.unpinManifest(applicationName, manifest)
}
