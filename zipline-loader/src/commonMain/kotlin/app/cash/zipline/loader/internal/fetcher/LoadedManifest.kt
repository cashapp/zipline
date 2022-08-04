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
 * A manifest plus the original bytes we loaded for it. We need the original bytes for signature
 * verification.
 */
data class LoadedManifest(
  val manifestBytes: ByteString,
  val manifest: ZiplineManifest,
  val freshAtEpochMs: Long,
) {
  fun encodeBuiltAtMs(): LoadedManifest {
    val builtManifest = manifest.copy(
      builtAtEpochMs = freshAtEpochMs
    )
    val builtManifestBytes = json.encodeToString(builtManifest).encodeUtf8()
    return LoadedManifest(builtManifestBytes, builtManifest, freshAtEpochMs)
  }
}

internal val json = Json {
  ignoreUnknownKeys = true
}

internal fun LoadedManifest(manifestBytes: ByteString, freshAtEpochMs: Long): LoadedManifest {
  val manifest = json.decodeFromString<ZiplineManifest>(manifestBytes.utf8())
  return LoadedManifest(manifestBytes, manifest, freshAtEpochMs)
}

internal fun LoadedManifest(manifestBytes: ByteString): LoadedManifest {
  val manifest = json.decodeFromString<ZiplineManifest>(manifestBytes.utf8())
  return LoadedManifest(manifestBytes, manifest, manifest.builtAtEpochMs!!)
}
