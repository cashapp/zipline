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

import android.content.Context
import app.cash.zipline.EventListener
import app.cash.zipline.Zipline
import app.cash.zipline.loader.internal.database.SqlDriverFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import okhttp3.OkHttpClient

internal actual fun Zipline.multiplatformLoadJsModule(bytecode: ByteArray, id: String) =
  loadJsModule(bytecode, id)

@OptIn(ExperimentalSerializationApi::class) // Zipline must track changes to EmptySerializersModule.
fun ZiplineLoader(
  context: Context,
  dispatcher: CoroutineDispatcher,
  httpClient: OkHttpClient,
  eventListener: EventListener = EventListener.NONE,
  serializersModule: SerializersModule = EmptySerializersModule,
  manifestVerifier: ManifestVerifier? = null,
): ZiplineLoader {
  return ZiplineLoader(
    sqlDriverFactory = SqlDriverFactory(context),
    dispatcher = dispatcher,
    httpClient = OkHttpZiplineHttpClient(okHttpClient = httpClient),
    eventListener = eventListener,
    serializersModule = serializersModule,
    manifestVerifier = manifestVerifier,
    embeddedDir = null,
    embeddedFileSystem = null,
    cache = null,
  )
}
