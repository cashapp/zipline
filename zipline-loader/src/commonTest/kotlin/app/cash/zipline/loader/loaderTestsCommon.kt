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
import app.cash.zipline.loader.ManifestVerifier.Companion.NO_SIGNATURE_CHECKS
import app.cash.zipline.loader.internal.cache.SqlDriverFactory
import app.cash.zipline.loader.internal.tink.subtle.Field25519
import app.cash.zipline.loader.internal.tink.subtle.KeyPair
import app.cash.zipline.loader.internal.tink.subtle.newKeyPairFromSeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.ByteString
import okio.FileSystem
import okio.Path

val EventListenerNoneFactory = EventListener.Factory { _, _ -> EventListener.NONE }

fun testZiplineLoader(
  dispatcher: CoroutineDispatcher,
  manifestVerifier: ManifestVerifier = NO_SIGNATURE_CHECKS,
  httpClient: ZiplineHttpClient,
  nowEpochMs: () -> Long,
  eventListenerFactory: EventListener.Factory,
) = ZiplineLoader(
  dispatcher = dispatcher,
  manifestVerifier = manifestVerifier,
  httpClient = httpClient,
  nowEpochMs = nowEpochMs,
).withEventListenerFactory(eventListenerFactory)

fun testZiplineCache(
  fileSystem: FileSystem,
  directory: Path,
  maxSizeInBytes: Long,
): ZiplineCache = ZiplineCache(testSqlDriverFactory(), fileSystem, directory, maxSizeInBytes)

internal expect fun testSqlDriverFactory(): SqlDriverFactory

fun randomToken() = randomByteString(8)

/** Returns a random byte string of size [size]. */
expect fun randomByteString(size: Int): ByteString

/** We don't implement ECDSA P-256 signing on non-JNI platforms. */
internal expect fun canSignEcdsaP256(): Boolean

@OptIn(ExperimentalSerializationApi::class)
fun prettyPrint(jsonString: String): String {
  val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }
  val jsonTree = json.decodeFromString(JsonElement.serializer(), jsonString)
  return json.encodeToString(JsonElement.serializer(), jsonTree)
}

/**
 * Returns a new `<publicKey / privateKey>` KeyPair. The PRNG for this function is not secure on all
 * platforms and should not be used for production code.
 */
internal fun generateKeyPairForTest(): KeyPair {
  return newKeyPairFromSeed(randomByteString(Field25519.FIELD_LEN))
}
