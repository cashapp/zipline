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
   */
  suspend fun fetch(
    id: String,
    sha256: ByteString,
    url: String,
    fileNameOverride: String? = null
  ): ByteString?
}

/**
 * Use a [concurrentDownloadsSemaphore] to control parallelism of fetching operations.
 */
suspend fun List<Fetcher>.fetch(
  concurrentDownloadsSemaphore: Semaphore,
  id: String,
  sha256: ByteString,
  url: String,
  fileNameOverride: String? = null,
): ByteString = concurrentDownloadsSemaphore
  .withPermit {
    var byteString: ByteString? = null
    for (fetcher in this) {
      byteString = fetcher.fetch(id, sha256, url, fileNameOverride)
      if (byteString != null) break
    }

    checkNotNull(byteString) {
      "Unable to get ByteString for [id=$id][sha256=$sha256][url=$url]"
    }
    return byteString
  }
