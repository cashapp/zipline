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
import okio.ByteString

/**
 * Fetch from the network.
 */
class HttpFetcher(
  private val httpClient: ZiplineHttpClient,
  private val eventListener: EventListener,
) : Fetcher {
  override suspend fun fetch(
    applicationId: String,
    id: String,
    sha256: ByteString,
    url: String
  ): ByteString = fetchByteString(applicationId, url, true)!!

  override suspend fun fetchManifest(
    applicationId: String,
    id: String,
    url: String
  ): ZiplineManifest? {
    val byteString = fetchByteString(applicationId, url, false)
    return byteString?.decodeToZiplineManifest(eventListener, applicationId, url)
  }

  override suspend fun pin(
    applicationId: String,
    manifest: ZiplineManifest
  ) {}

  private suspend fun fetchByteString(
    applicationId: String,
    url: String,
    throwException: Boolean
  ): ByteString? {
    eventListener.downloadStart(applicationId, url)
    val byteString = try {
      httpClient.download(url)
    } catch (e: Exception) {
      eventListener.downloadFailed(applicationId, url, e)
      if (throwException) {
        throw e
      } else {
        return null
      }
    }
    eventListener.downloadEnd(applicationId, url)
    return byteString
  }
}
