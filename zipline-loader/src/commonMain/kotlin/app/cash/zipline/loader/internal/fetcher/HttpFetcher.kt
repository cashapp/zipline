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
import kotlinx.serialization.json.jsonPrimitive
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
    url: String,
  ) = fetchByteString(applicationName, url)

  suspend fun fetchManifest(
    applicationName: String,
    url: String,
    freshAtEpochMs: Long,
  ): LoadedManifest {
    val manifestBytesWithRelativeUrls = fetchByteString(applicationName, url)

    try {
      val manifestJsonElementWithRelativeUrls =
        jsonForManifest.parseToJsonElement(manifestBytesWithRelativeUrls.utf8())
      val manifestJsonElement = resolveUrls(manifestJsonElementWithRelativeUrls, url)
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
   * Returns a manifest equivalent to [manifest], but with module URLs resolved against [baseUrl].
   * This way consumers of the manifest don't need to know the URL that the manifest was downloaded
   * from.
   *
   * This operates on the JSON model and not the decoded model so unknown values are not lost when
   * the updated JSON is written to disk.
   */
  internal fun resolveUrls(manifest: JsonElement, baseUrl: String): JsonElement {
    val newContent = manifest.jsonObject.toMutableMap()

    val modules = newContent["modules"]
    if (modules != null) {
      val newModules = mutableMapOf<String, JsonElement>()
      for ((key, module) in modules.jsonObject) {
        val newModule = module.jsonObject.toMutableMap()
        val url = newModule["url"]
        if (url != null) {
          val urlString = url.jsonPrimitive.content
          newModule["url"] = JsonPrimitive(httpClient.resolve(baseUrl, urlString))
        }
        newModules[key] = JsonObject(newModule)
      }
      newContent["modules"] = JsonObject(newModules)
    }

    return JsonObject(newContent)
  }

  private suspend fun fetchByteString(
    applicationName: String,
    url: String,
  ): ByteString {
    eventListener.downloadStart(applicationName, url)
    val result = try {
      httpClient.download(url)
    } catch (e: Exception) {
      eventListener.downloadFailed(applicationName, url, e)
      throw e
    }
    eventListener.downloadEnd(applicationName, url)
    return result
  }
}
