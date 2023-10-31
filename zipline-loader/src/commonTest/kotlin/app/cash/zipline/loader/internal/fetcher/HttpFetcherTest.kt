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

import app.cash.zipline.loader.FakeZiplineHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class HttpFetcherTest {
  private val httpFetcher = HttpFetcher(FakeZiplineHttpClient())
  private val json = Json {
    prettyPrint = true
  }

  @Test
  fun happyPath() {
    val manifestWithRelativeUrls =
      """
      |{
      |    "modules": {
      |        "./hello.js": {
      |            "url": "hello.zipline",
      |            "sha256": "6bd4baa9f46afa62477fec8c9e95528de7539f036d26fc13885177b32fc0d6ab"
      |        },
      |        "./jello.js": {
      |            "url": "jello.zipline",
      |            "sha256": "7af37185091e22463ff627686aedfec3528376eb745026fae1d6153688885e73"
      |        }
      |    }
      |}
      """.trimMargin()

    val manifestWithBaseUrl = httpFetcher.withBaseUrl(
      manifest = json.parseToJsonElement(manifestWithRelativeUrls),
      baseUrl = "https://example.com/path/",
    )

    assertEquals(
      """
      |{
      |    "unsigned": {
      |        "baseUrl": "https://example.com/path/"
      |    },
      |    "modules": {
      |        "./hello.js": {
      |            "url": "hello.zipline",
      |            "sha256": "6bd4baa9f46afa62477fec8c9e95528de7539f036d26fc13885177b32fc0d6ab"
      |        },
      |        "./jello.js": {
      |            "url": "jello.zipline",
      |            "sha256": "7af37185091e22463ff627686aedfec3528376eb745026fae1d6153688885e73"
      |        }
      |    }
      |}
      """.trimMargin(),
      json.encodeToString(JsonElement.serializer(), manifestWithBaseUrl),
    )
  }

  @Test
  fun withBaseUrlRetainsUnknownFields() {
    val manifestWithRelativeUrls =
      """
      |{
      |    "unknown string": "hello",
      |    "modules": {
      |        "./hello.js": {
      |            "url": "hello.zipline",
      |            "sha256": "6bd4baa9f46afa62477fec8c9e95528de7539f036d26fc13885177b32fc0d6ab"
      |        }
      |    }
      |}
      """.trimMargin()

    val manifestWithResolvedUrls = httpFetcher.withBaseUrl(
      manifest = json.parseToJsonElement(manifestWithRelativeUrls),
      baseUrl = "https://example.com/path/",
    )

    assertEquals(
      """
      |{
      |    "unsigned": {
      |        "baseUrl": "https://example.com/path/"
      |    },
      |    "unknown string": "hello",
      |    "modules": {
      |        "./hello.js": {
      |            "url": "hello.zipline",
      |            "sha256": "6bd4baa9f46afa62477fec8c9e95528de7539f036d26fc13885177b32fc0d6ab"
      |        }
      |    }
      |}
      """.trimMargin(),
      json.encodeToString(JsonElement.serializer(), manifestWithResolvedUrls),
    )
  }
}
