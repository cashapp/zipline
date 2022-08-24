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
package app.cash.zipline.loader

import app.cash.zipline.EventListener
import app.cash.zipline.loader.internal.systemEpochMsClock
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient

fun ZiplineLoader(
  dispatcher: CoroutineDispatcher,
  manifestVerifier: ManifestVerifier,
  httpClient: OkHttpClient,
  eventListener: EventListener = EventListener.NONE,
  nowEpochMs: () -> Long = systemEpochMsClock,
): ZiplineLoader {
  return ZiplineLoader(
    dispatcher = dispatcher,
    manifestVerifier = manifestVerifier,
    httpClient = httpClient.asZiplineHttpClient(),
    eventListener = eventListener,
    nowEpochMs = nowEpochMs,
  )
}

fun OkHttpClient.asZiplineHttpClient(): ZiplineHttpClient = OkHttpZiplineHttpClient(this)
