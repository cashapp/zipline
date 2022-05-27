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
import app.cash.zipline.loader.fetcher.Fetcher
import app.cash.zipline.loader.fetcher.HttpFetcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import okhttp3.OkHttpClient

@OptIn(ExperimentalSerializationApi::class)
fun ZiplineLoader(
  dispatcher: CoroutineDispatcher,
  httpClient: OkHttpClient,
  serializersModule: SerializersModule = EmptySerializersModule,
  eventListener: EventListener = EventListener.NONE,
): ZiplineLoader = ZiplineLoader(
  dispatcher = dispatcher,
  eventListener = eventListener,
  serializersModule = serializersModule,
  httpClient = OkHttpZiplineHttpClient(okHttpClient = httpClient),
)
