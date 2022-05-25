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
import app.cash.zipline.loader.OkHttpZiplineHttpClient
import okhttp3.OkHttpClient

fun HttpFetcher(
  httpClient: OkHttpClient,
  eventListener: EventListener = EventListener.NONE
) = HttpFetcher(OkHttpZiplineHttpClient(httpClient), eventListener)
