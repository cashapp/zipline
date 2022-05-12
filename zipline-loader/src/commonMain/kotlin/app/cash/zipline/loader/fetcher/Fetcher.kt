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
 *
 * If an interceptor does not get a file, it returns null and the next [Fetcher] is called.
 */
interface Fetcher {
  /**
   * Get the desired [ByteString] or null if not found.
   *
   * @param manifestForApplicationId when fetching a manifest, if fallback on failure functionality
   *   is desired, set to the application id, and the last working pinned manifest will be returned
   *   from the cache.
   */
  suspend fun fetch(
    applicationId: String,
    sha256: ByteString,
    url: String,
    /**
     * When fetching a manifest, use the following to identify which application it is for.
     * For fallback purposes, this parameter informs the on disk name and treatment by
     *   caching and embedded fetchers which can be very different from the treatment of
     *   hoh-manifest Zipline Javascript module fetching.
     */
    manifestForApplicationId: String? = null
  ): ByteString?

  /**
   * After a successful application load, if fallback on failure functionality is enabled, pin the
   *   manifest and relevant Zipline files as a stable fallback for future failed load attempts.
   * A fetcher will return true if it has pinned successfully and remaining fetchers
   *   will not have their pin function called.
   */
  suspend fun pin(
    applicationId: String,
    manifest: ZiplineManifest,
    manifestByteString: ByteString,
  ): Boolean
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
  manifestByteString: ByteString,
): Boolean {
  var pinned = false
  for (fetcher in this) {
    pinned = fetcher.pin(applicationId, manifest, manifestByteString)
    if (pinned) break
  }

  check(pinned) {
    "Unable to pin stable application load for [applicationId=$applicationId]"
  }
  return pinned
}
