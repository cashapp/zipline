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
import app.cash.zipline.loader.internal.cache.SqlDriverFactory
import app.cash.zipline.loader.internal.fetcher.HttpFetcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import okhttp3.OkHttpClient

fun ZiplineLoader(
  dispatcher: CoroutineDispatcher,
  manifestVerifier: ManifestVerifier,
  httpClient: ZiplineHttpClient,
  eventListener: EventListener = EventListener.NONE,
  nowEpochMs: () -> Long = { System.currentTimeMillis() },
  serializersModule: SerializersModule = EmptySerializersModule(),
): ZiplineLoader {
  return ZiplineLoader(
    sqlDriverFactory = SqlDriverFactory(),
    dispatcher = dispatcher,
    manifestVerifier = manifestVerifier,
    httpFetcher = HttpFetcher(httpClient, eventListener),
    eventListener = eventListener,
    nowEpochMs = nowEpochMs,
    serializersModule = serializersModule,
    embeddedDir = null,
    embeddedFileSystem = null,
    cache = null,
  )
}

fun ZiplineLoader(
  dispatcher: CoroutineDispatcher,
  manifestVerifier: ManifestVerifier,
  httpClient: OkHttpClient,
  eventListener: EventListener = EventListener.NONE,
  nowEpochMs: () -> Long = { System.currentTimeMillis() },
  serializersModule: SerializersModule = EmptySerializersModule()
): ZiplineLoader {
  return ZiplineLoader(
    dispatcher = dispatcher,
    manifestVerifier = manifestVerifier,
    httpClient = OkHttpZiplineHttpClient(httpClient),
    nowEpochMs = nowEpochMs,
    eventListener = eventListener,
    serializersModule = serializersModule
  )
}
