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
package app.cash.zipline.loader.fetcher

import app.cash.zipline.EventListener
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.ZiplineManifest
import app.cash.zipline.loader.ZiplineManifest.Companion.decodeToZiplineManifest
import app.cash.zipline.loader.resolveUrls
import okio.ByteString

/**
 * Download resources from the network. If the download fails, the exception is reported to
 * [eventListener] and this fetcher returns null.
 */
class HttpFetcher(
  private val httpClient: ZiplineHttpClient,
  private val eventListener: EventListener = EventListener.NONE,
) : Fetcher {
  override suspend fun fetch(
    applicationName: String,
    id: String,
    sha256: ByteString,
    url: String,
  ) = fetchByteString(applicationName, url)

  override suspend fun fetchManifest(
    applicationName: String,
    id: String,
    url: String?,
  ): ZiplineManifest? {
    if (url == null) return null // This fetcher requires URLs.
    val byteString = fetchByteString(applicationName, url)
    val originalManifest = byteString.decodeToZiplineManifest(eventListener, applicationName, url)
    return httpClient.resolveUrls(originalManifest, url)
  }

  override suspend fun pin(
    applicationName: String,
    manifest: ZiplineManifest,
  ) {
  }

  override suspend fun unpin(applicationName: String, manifest: ZiplineManifest) {
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
