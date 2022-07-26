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
package app.cash.zipline.samples.emojisearch

import app.cash.zipline.Zipline
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.ZiplineLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

class EmojiSearchZipline(
  private val httpClient: ZiplineHttpClient,
  private val hostApi: HostApi,
) {
  private val coroutineScope: CoroutineScope = MainScope()
  private val dispatcher = Dispatchers.Main
  private val eventFlow: MutableStateFlow<EmojiSearchEvent> = MutableStateFlow(
    value = EmojiSearchEvent.SearchTermEvent(searchTerm = "")
  )
  private var modelFlow: Flow<EmojiSearchViewModel>? = null

  private val manifestUrl = "http://localhost:8080/manifest.zipline.json"

  private val ziplineLoader = ZiplineLoader(
    dispatcher = dispatcher,
    httpClient = httpClient,
    nowEpochMs = { NSDate().timeIntervalSince1970().toLong() * 1000 },
  )

  private var zipline: Zipline? = null

  fun setUp(resultsNotifier: (EmojiSearchViewModel) -> Unit = {}) {
    coroutineScope.launch(dispatcher) {
      val zipline = ziplineLoader.loadOnce("emojiSearch", manifestUrl) {
        it.bind<HostApi>("hostApi", hostApi)
      }.zipline
      this@EmojiSearchZipline.zipline = zipline

      val presenter = zipline.take<EmojiSearchPresenter>("emojiSearchPresenter")
      modelFlow = presenter.produceModels(eventFlow)
      modelFlow!!.collect {
        resultsNotifier(it)
      }
    }
  }

  fun updateSearchTerm(searchTerm: String) {
    coroutineScope.launch(dispatcher) {
      eventFlow.value = EmojiSearchEvent.SearchTermEvent(searchTerm = searchTerm)
    }
  }
}
