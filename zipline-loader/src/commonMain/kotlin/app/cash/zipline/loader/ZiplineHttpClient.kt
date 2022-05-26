/*
 * Copyright (C) 2021 Square, Inc.
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

import okio.ByteString

interface ZiplineHttpClient {
  suspend fun download(url: String): ByteString

  /**
   * Returns the URL of [link] relative to [baseUrl].
   *
   * Resolve can be called multiple times on a [link] that has already been resolved.
   */
  fun resolve(baseUrl: String, link: String): String
}

/**
 * Returns a manifest equivalent to [manifest], but with module URLs resolved against [baseUrl].
 * This way consumers of the manifest don't need to know the URL that the manifest was downloaded
 * from.
 */
internal fun ZiplineHttpClient.resolveUrls(
  manifest: ZiplineManifest,
  baseUrl: String
): ZiplineManifest {
  return manifest.copy(
    modules = manifest.modules.mapValues { (_, module) ->
      module.copy(url = resolve(baseUrl, module.url))
    }
  )
}
