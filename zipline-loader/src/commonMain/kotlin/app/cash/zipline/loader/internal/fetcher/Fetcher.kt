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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.ByteString

/**
 * Fetchers get a desired [ByteString] if possible.
 */
internal interface Fetcher {
  /**
   * Returns the desired [ByteString], or null if this fetcher doesn't know how to fetch this
   * resource.
   *
   * @throws Exception if this fetcher knows how to fetch this resource but was unsuccessful.
   */
  suspend fun fetch(
    applicationName: String,
    eventListener: EventListener,
    id: String,
    sha256: ByteString,
    nowEpochMs: Long,
    baseUrl: String?,
    url: String,
  ): ByteString?
}

/**
 * Use a [concurrentDownloadsSemaphore] to control parallelism of fetching operations.
 */
internal suspend fun List<Fetcher>.fetch(
  concurrentDownloadsSemaphore: Semaphore,
  applicationName: String,
  eventListener: EventListener,
  id: String,
  sha256: ByteString,
  nowEpochMs: Long,
  baseUrl: String?,
  url: String,
): ByteString? = concurrentDownloadsSemaphore.withPermit {
  var firstException: Exception? = null
  for (fetcher in this) {
    try {
      return@withPermit fetcher.fetch(
        applicationName = applicationName,
        eventListener = eventListener,
        id = id,
        sha256 = sha256,
        nowEpochMs = nowEpochMs,
        baseUrl = baseUrl,
        url = url,
      ) ?: continue
    } catch (e: Exception) {
      if (firstException == null) {
        firstException = e
      }
    }
  }
  if (firstException != null) throw firstException
  return@withPermit null
}
