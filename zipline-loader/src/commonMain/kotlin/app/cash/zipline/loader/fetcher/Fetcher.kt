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
 * A list of [Fetcher] delegate in order responsibility of getting the desired [ByteString].
 */
interface Fetcher {
  /**
   * Get the desired [ByteString] or null if not found.
   *
   * If this fetcher supports pinning, the returned value will be pinned for [applicationId] until
   *   [pin] is called.
   * If a fetcher does not get a file, it returns null and the next [Fetcher] is called.
   */
  suspend fun fetch(
    applicationId: String,
    sha256: ByteString,
    url: String,
  ): ByteString?

  /**
   * Get the manifest for [applicationId] or null if not found.
   * If a fetcher does not get a file, it returns null and the next [Fetcher] is called.
   */
  suspend fun fetchManifest(
    applicationId: String,
    url: String,
  ): ByteString?

  /**
   * Permits all downloads for [applicationId] not in [manifest] to be pruned.
   *
   * This assumes all artifacts in [manifest] are currently pinned, but it does not enforce this
   * assumption.
   *
   * Pin is called on all fetchers when a load succeeds.
   */
  suspend fun pin(
    applicationId: String,
    manifest: ZiplineManifest,
  )
}

/**
 * Use a [concurrentDownloadsSemaphore] to control parallelism of fetching operations.
 */
suspend fun List<Fetcher>.fetch(
  concurrentDownloadsSemaphore: Semaphore,
  applicationId: String,
  id: String,
  sha256: ByteString,
  url: String,
  manifestForApplicationId: String? = null,
): ByteString = concurrentDownloadsSemaphore
  .withPermit {
    var byteString: ByteString? = null
    for (fetcher in this) {
      byteString = fetcher.fetch(applicationId, sha256, url, manifestForApplicationId)
      if (byteString != null) break
    }

    checkNotNull(byteString) {
      "Unable to get ByteString for [applicationId=$applicationId][id=$id][sha256=$sha256][url=$url]"
    }
    return byteString
  }

suspend fun List<Fetcher>.pin(
  applicationId: String,
  manifest: ZiplineManifest,
)= this.forEach { fetcher ->
    fetcher.pin(applicationId, manifest)
  }
