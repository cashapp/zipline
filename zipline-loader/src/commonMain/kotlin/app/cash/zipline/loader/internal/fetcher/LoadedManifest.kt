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

import app.cash.zipline.ZiplineManifest
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
    val freshManifestBytes = freshManifest.encodeJson().encodeUtf8()
    return LoadedManifest(freshManifestBytes, freshManifest, freshAtEpochMs)
  }
}

internal fun LoadedManifest(manifestBytes: ByteString, freshAtEpochMs: Long): LoadedManifest {
  val manifest = ZiplineManifest.decodeJson(manifestBytes.utf8())
  return LoadedManifest(manifestBytes, manifest, freshAtEpochMs)
}

internal fun LoadedManifest(manifestBytes: ByteString): LoadedManifest {
  val manifest = ZiplineManifest.decodeJson(manifestBytes.utf8())
  return LoadedManifest(manifestBytes, manifest, manifest.freshAtEpochMs!!)
}
