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
import okio.IOException

class FakeZiplineHttpClient: ZiplineHttpClient {
  var filePathToByteString: Map<String, ByteString> = mapOf()

  override suspend fun download(url: String): ByteString {
    return filePathToByteString[url] ?: throw IOException("404: $url not found")
  }

  /** Note: this naive resolve() that doesn't support `../` etc. */
  override fun resolve(baseUrl: String, link: String): String {
    val calculatedBaseUrl = baseUrl.substringBeforeLast("/")
    return if (link.startsWith(calculatedBaseUrl)) {
      link
    } else {
      "$calculatedBaseUrl/$link"
    }
  }
}
