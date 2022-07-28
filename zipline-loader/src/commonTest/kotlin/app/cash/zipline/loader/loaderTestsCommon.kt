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
import kotlinx.coroutines.CoroutineDispatcher
import okio.ByteString
import okio.FileSystem

expect val systemFileSystem: FileSystem

expect fun testZiplineLoader(
  dispatcher: CoroutineDispatcher,
  httpClient: ZiplineHttpClient,
  eventListener: EventListener = EventListener.NONE,
  manifestVerifier: ManifestVerifier? = null,
): ZiplineLoader

internal expect fun testSqlDriverFactory(): SqlDriverFactory

fun randomToken() = randomByteString(8)

/** Returns a random byte string of size [size]. */
expect fun randomByteString(size: Int): ByteString
