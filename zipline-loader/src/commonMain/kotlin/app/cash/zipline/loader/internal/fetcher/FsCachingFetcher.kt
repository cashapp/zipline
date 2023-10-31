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
package app.cash.zipline.loader.internal.fetcher

import app.cash.zipline.EventListener
import app.cash.zipline.loader.ZiplineCache
import okio.ByteString

/**
 * Fetch from the network and save to local fileSystem cache once downloaded.
 */
internal class FsCachingFetcher(
  private val cache: ZiplineCache,
  private val delegate: Fetcher,
) : Fetcher {
  override suspend fun fetch(
    applicationName: String,
    eventListener: EventListener,
    id: String,
    sha256: ByteString,
    nowEpochMs: Long,
    baseUrl: String?,
    url: String,
  ): ByteString? {
    return cache.getOrPut(applicationName, sha256, nowEpochMs) {
      delegate.fetch(applicationName, eventListener, id, sha256, nowEpochMs, baseUrl, url)
    }
  }

  fun loadPinnedManifest(applicationName: String, nowEpochMs: Long): LoadedManifest? {
    return cache.getPinnedManifest(applicationName, nowEpochMs)
  }

  /**
   * Permits all downloads for [applicationName] not in [loadedManifest] to be pruned.
   *
   * This assumes that all artifacts in [loadedManifest] are currently pinned. Fetchers do not
   * necessarily enforce this assumption.
   */
  fun pin(applicationName: String, loadedManifest: LoadedManifest, nowEpochMs: Long) =
    cache.pinManifest(applicationName, loadedManifest, nowEpochMs)

  /**
   * Removes the pins for [applicationName] in [loadedManifest] so they may be pruned.
   */
  fun unpin(applicationName: String, loadedManifest: LoadedManifest, nowEpochMs: Long) =
    cache.unpinManifest(applicationName, loadedManifest, nowEpochMs)

  /**
   * Updates freshAt timestamp for manifests that in later network fetch is still the freshest.
   */
  fun updateFreshAt(applicationName: String, loadedManifest: LoadedManifest, nowEpochMs: Long) =
    cache.updateManifestFreshAt(applicationName, loadedManifest, nowEpochMs)
}
