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

import app.cash.zipline.EventListener
import app.cash.zipline.loader.ZiplineHttpClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * Download resources from the network. If the download fails, the exception is reported to
 * [eventListener] and this fetcher returns null.
 */
internal class HttpFetcher(
  private val httpClient: ZiplineHttpClient,
  private val eventListener: EventListener = EventListener.NONE,
) : Fetcher {
  override suspend fun fetch(
    applicationName: String,
    id: String,
    sha256: ByteString,
    baseUrl: String?,
    url: String,
  ) = fetchByteString(
    applicationName = applicationName,
    baseUrl = baseUrl,
    url = url,
  )

  suspend fun fetchManifest(
    applicationName: String,
    url: String,
    freshAtEpochMs: Long,
  ): LoadedManifest {
    val manifestBytesWithoutBaseUrl = fetchByteString(applicationName, null, url)

    try {
      val manifestJsonElementWithoutBaseUrl =
        jsonForManifest.parseToJsonElement(manifestBytesWithoutBaseUrl.utf8())
      val manifestJsonElement = withBaseUrl(manifestJsonElementWithoutBaseUrl, url)
      val manifestJson = jsonForManifest.encodeToString(
        JsonElement.serializer(),
        manifestJsonElement
      )
      return LoadedManifest(
        manifestBytes = manifestJson.encodeUtf8(),
        manifest = jsonForManifest.decodeFromJsonElement(manifestJsonElement),
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
    baseUrl: String?,
    url: String,
  ): ByteString {
    val fullUrl = when {
      baseUrl != null -> httpClient.resolve(baseUrl, url)
      else -> url
    }

    eventListener.downloadStart(applicationName, fullUrl)
    val result = try {
      httpClient.download(fullUrl)
    } catch (e: Exception) {
      eventListener.downloadFailed(applicationName, fullUrl, e)
      throw e
    }
    eventListener.downloadEnd(applicationName, fullUrl)

    return result
  }
}
