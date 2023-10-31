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
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.

package app.cash.zipline.loader.internal.fetcher

import app.cash.zipline.EventListener
import app.cash.zipline.internal.MANIFEST_MAX_SIZE
import app.cash.zipline.internal.decodeToManifest
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.internal.resolveUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * Download resources from the network. If the download fails, the exception is reported to
 * [EventListener] and this fetcher returns null.
 */
internal class HttpFetcher(
  private val httpClient: ZiplineHttpClient,
) : Fetcher {
  override suspend fun fetch(
    applicationName: String,
    eventListener: EventListener,
    id: String,
    sha256: ByteString,
    nowEpochMs: Long,
    baseUrl: String?,
    url: String,
  ) = fetchByteString(
    applicationName = applicationName,
    eventListener = eventListener,
    baseUrl = baseUrl,
    url = url,
    requestHeaders = ZIPLINE_REQUEST_HEADERS,
  )

  suspend fun fetchManifest(
    applicationName: String,
    eventListener: EventListener,
    url: String,
    freshAtEpochMs: Long,
  ): LoadedManifest {
    val manifestBytesWithoutBaseUrlUtf8 = fetchByteString(
      applicationName = applicationName,
      eventListener = eventListener,
      baseUrl = null,
      url = url,
      requestHeaders = MANIFEST_REQUEST_HEADERS,
    )
    val manifestBytesWithoutBaseUrl = manifestBytesWithoutBaseUrlUtf8.utf8()
    check(manifestBytesWithoutBaseUrl.length <= MANIFEST_MAX_SIZE) {
      "manifest larger than $MANIFEST_MAX_SIZE: ${manifestBytesWithoutBaseUrl.length}"
    }

    try {
      val manifestJsonElementWithoutBaseUrl = Json.parseToJsonElement(manifestBytesWithoutBaseUrl)
      val manifestJsonElement = withBaseUrl(manifestJsonElementWithoutBaseUrl, url)
      val manifestJson = Json.encodeToString(
        JsonElement.serializer(),
        manifestJsonElement,
      )
      return LoadedManifest(
        manifestBytes = manifestJson.encodeUtf8(),
        manifest = manifestJsonElement.decodeToManifest(),
        freshAtEpochMs = freshAtEpochMs,
      )
    } catch (e: Exception) {
      eventListener.manifestParseFailed(applicationName, url, e)
      throw e
    }
  }

  /**
   * Returns a manifest equivalent to [manifest], but with a baseUrl property set. This way
   * consumers of the manifest don't need to know the URL that the manifest was downloaded from.
   *
   * This operates on the JSON model and not the decoded model so unknown values are not lost when
   * the updated JSON is written to disk.
   */
  internal fun withBaseUrl(manifest: JsonElement, baseUrl: String): JsonElement {
    val content = manifest.jsonObject.toMutableMap()

    val unsigned = content.remove("unsigned")?.jsonObject?.toMutableMap() ?: mutableMapOf()
    unsigned.remove("baseUrl")

    val newUnsigned = mutableMapOf<String, JsonElement>()
    newUnsigned["baseUrl"] = JsonPrimitive(baseUrl)
    newUnsigned.putAll(unsigned)

    val newContent = mutableMapOf<String, JsonElement>()
    newContent["unsigned"] = JsonObject(newUnsigned)
    newContent.putAll(content)

    return JsonObject(newContent)
  }

  private suspend fun fetchByteString(
    applicationName: String,
    eventListener: EventListener,
    baseUrl: String?,
    url: String,
    requestHeaders: List<Pair<String, String>>,
  ): ByteString {
    val fullUrl = when {
      baseUrl != null -> resolveUrl(baseUrl, url)
      else -> url
    }

    val startValue = eventListener.downloadStart(applicationName, fullUrl)
    val result = try {
      httpClient.download(fullUrl, requestHeaders)
    } catch (e: Exception) {
      eventListener.downloadFailed(applicationName, fullUrl, e, startValue)
      throw e
    }
    eventListener.downloadEnd(applicationName, fullUrl, startValue)

    return result
  }

  companion object {
    /** Headers for the HTTP GET request for the manifest file. */
    val MANIFEST_REQUEST_HEADERS = listOf<Pair<String, String>>()

    /**
     * Headers for the HTTP GET request for the .zipline files.
     *
     * We use no-store because `ZiplineLoader` already stores these keyed by their hash. Storing
     * only once potentially saves disk & compute.
     */
    val ZIPLINE_REQUEST_HEADERS = listOf("Cache-Control" to "no-store")
  }
}
