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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class) // Zipline must track changes to EmptySerializersModule.
fun ZiplineLoader(
  dispatcher: CoroutineDispatcher,
  httpClient: ZiplineHttpClient,
  eventListener: EventListener = EventListener.NONE,
  nowEpochMs: () -> Long,
  serializersModule: SerializersModule = EmptySerializersModule,
  manifestVerifier: ManifestVerifier? = null,
): ZiplineLoader {
  return ZiplineLoader(
    sqlDriverFactory = SqlDriverFactory(),
    dispatcher = dispatcher,
    httpFetcher = HttpFetcher(httpClient, eventListener),
    eventListener = eventListener,
    nowEpochMs = nowEpochMs,
    serializersModule = serializersModule,
    manifestVerifier = manifestVerifier,
    embeddedDir = null,
    embeddedFileSystem = null,
    cache = null,
  )
}
