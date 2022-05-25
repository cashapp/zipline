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

import app.cash.zipline.loader.ZiplineManifest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.ByteString

/**
 * Fetchers get a desired [ByteString] if possible.
 */
interface Fetcher {
  /**
   * Returns the desired [ByteString], or null if not found.
   *
   * If this fetcher supports pinning, the returned value will be pinned for [applicationName] until
   * [pinManifest] is called.
   *
   * If a fetcher cannot get a file, it returns null. The next [Fetcher] should be called.
   */
  suspend fun fetch(
    applicationName: String,
    id: String,
    sha256: ByteString,
    url: String,
  ): ByteString?

  /**
   * Returns the manifest for [applicationName], or null if not found.
   *
   * If a fetcher cannot get a file, it returns null. The next [Fetcher] should be called.
   */
  suspend fun fetchManifest(
    applicationName: String,
    id: String,
    url: String,
  ): ZiplineManifest?

  /**
   * Permits all downloads for [applicationName] not in [manifest] to be pruned.
   *
   * This assumes that all artifacts in [manifest] are currently pinned. Fetchers do not necessarily
   * enforce this assumption.
   *
   * This function is called on all fetchers once a load succeeds.
   */
  suspend fun pinManifest(
    applicationName: String,
    manifest: ZiplineManifest,
  )

  /**
   * Removes the pins for [applicationName] in [manifest] so they may be pruned.
   *
   * This function is called on all fetchers once a load fails.
   */
  suspend fun unpinManifest(
    applicationName: String,
    manifest: ZiplineManifest,
  )
}

/**
 * Use a [concurrentDownloadsSemaphore] to control parallelism of fetching operations.
 */
suspend fun List<Fetcher>.fetch(
  concurrentDownloadsSemaphore: Semaphore,
  applicationName: String,
  id: String,
  sha256: ByteString,
  url: String,
): ByteString = concurrentDownloadsSemaphore
  .withPermit {
    var byteString: ByteString? = null
    for (fetcher in this) {
      byteString = fetcher.fetch(applicationName, id, sha256, url)
      if (byteString != null) break
    }

    checkNotNull(byteString) {
      "Unable to fetch ByteString for [applicationName=$applicationName][id=$id][sha256=$sha256][url=$url]"
    }
    return byteString
  }

/**
 * Use a [concurrentDownloadsSemaphore] to control parallelism of fetching operations.
 */
suspend fun List<Fetcher>.fetchManifest(
  concurrentDownloadsSemaphore: Semaphore,
  applicationName: String,
  id: String,
  url: String,
): ZiplineManifest = concurrentDownloadsSemaphore
  .withPermit {
    var manifest: ZiplineManifest? = null
    for (fetcher in this) {
      manifest = fetcher.fetchManifest(applicationName, id, url)
      if (manifest != null) break
    }

    checkNotNull(manifest) {
      "Unable to fetch Manifest for [applicationName=$applicationName][id=$id][url=$url]"
    }
    return manifest
  }

suspend fun List<Fetcher>.pinManifest(
  applicationName: String,
  manifest: ZiplineManifest,
) = this.forEach { fetcher ->
  fetcher.pinManifest(applicationName, manifest)
}

suspend fun List<Fetcher>.unpinManifest(
  applicationName: String,
  manifest: ZiplineManifest,
) = this.forEach { fetcher ->
  fetcher.unpinManifest(applicationName, manifest)
}
