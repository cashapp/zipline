package app.cash.zipline.samples.emojisearch

import kotlinx.coroutines.flow.Flow

interface CoroutinePresenter<ViewEvent, ViewModel> {
  suspend fun produceModels(events: Flow<ViewEvent>, models: suspend (ViewModel) -> Unit)
}
