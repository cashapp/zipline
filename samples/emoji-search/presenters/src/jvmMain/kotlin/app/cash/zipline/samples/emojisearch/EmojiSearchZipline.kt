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
import app.cash.zipline.asFlowReference
import java.util.concurrent.Executors
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch

class EmojiSearchZipline {
  private val executorService = Executors.newSingleThreadExecutor { Thread(it, "Zipline") }
  private val dispatcher = executorService.asCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher)
  private val hostApi = RealHostApi()

  fun produceModelsIn(
    coroutineScope: CoroutineScope,
    eventFlow: Flow<EmojiSearchEvent>,
    modelsStateFlow: MutableStateFlow<EmojiSearchViewModel>
  ) {
    val job = coroutineScope.launch(dispatcher) {
      val presentersJs = hostApi.httpCall("http://10.0.2.2:8080/presenters.js", mapOf())
      zipline.quickJs.evaluate(presentersJs, "presenters.js")
      zipline.set<HostApi>("hostApi", hostApi)
      zipline.quickJs.evaluate(
        "presenters.app.cash.zipline.samples.emojisearch.preparePresenters()"
      )
      val presenter = zipline.get<EmojiSearchPresenter>("emojiSearchPresenter")

      val eventsFlowReference = eventFlow.asFlowReference()
      val modelsFlowReference = presenter.produceModels(eventsFlowReference)

      val modelsFlow = modelsFlowReference.get()
      modelsStateFlow.emitAll(modelsFlow)
    }

    job.invokeOnCompletion {
      dispatcher.dispatch(EmptyCoroutineContext) { zipline.close() }
      executorService.shutdown()
    }
  }
}
