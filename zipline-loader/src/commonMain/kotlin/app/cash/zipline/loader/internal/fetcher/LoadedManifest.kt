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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * A manifest plus the original bytes we loaded for it.
 *
 * @param manifestBytes: the original bytes are necessary for signature verification.
 */
data class LoadedManifest(
  val manifestBytes: ByteString,
  val manifest: ZiplineManifest,
  val freshAtEpochMs: Long,
) {
  fun encodeFreshAtMs(): LoadedManifest {
    val freshManifest = manifest.copy(
      freshAtEpochMs = freshAtEpochMs,
    )
    val freshManifestBytes = jsonForManifest.encodeToString(freshManifest).encodeUtf8()
    return LoadedManifest(freshManifestBytes, freshManifest, freshAtEpochMs)
  }
}

internal val jsonForManifest = Json {
  // For backwards-compatibility, allow new fields to be introduced.
  ignoreUnknownKeys = true

  // Because new releases may change default values, it's best to encode them.
  encodeDefaults = true
}

internal fun LoadedManifest(manifestBytes: ByteString, freshAtEpochMs: Long): LoadedManifest {
  val manifest = jsonForManifest.decodeFromString<ZiplineManifest>(manifestBytes.utf8())
  return LoadedManifest(manifestBytes, manifest, freshAtEpochMs)
}

internal fun LoadedManifest(manifestBytes: ByteString): LoadedManifest {
  val manifest = jsonForManifest.decodeFromString<ZiplineManifest>(manifestBytes.utf8())
  return LoadedManifest(manifestBytes, manifest, manifest.freshAtEpochMs!!)
}
