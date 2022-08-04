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

import app.cash.zipline.loader.ZiplineManifest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.ByteString

/**
 * A manifest plus the original bytes we loaded for it. We need the original bytes for signature
 * verification.
 *
 * @param freshAtMs is only set by the [HttpFetcher]
 */
data class LoadedManifest(
  val manifestBytes: ByteString,
  val manifest: ZiplineManifest,
  val freshAtMs: Long? = null,
)

internal val json = Json {
  ignoreUnknownKeys = true
}

internal fun LoadedManifest(manifestBytes: ByteString, freshAtMs: Long? = null): LoadedManifest {
  val manifest = json.decodeFromString<ZiplineManifest>(manifestBytes.utf8())
  return LoadedManifest(manifestBytes, manifest, freshAtMs)
}
