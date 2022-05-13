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
    sha256: ByteString,
    url: String,
    manifestForApplicationId: String?
  ): ByteString? {
    eventListener.downloadStart(applicationId, url)
    val byteString = try {
      httpClient.download(url)
    } catch (e: Exception) {
      eventListener.downloadFailed(applicationId, url, e)
      throw e
    }
    eventListener.downloadEnd(applicationId, url)
    return byteString
  }

  override suspend fun pin(
    applicationId: String,
    manifest: ZiplineManifest
  ): Boolean = false
}
