package app.cash.zipline.samples.emojisearch

import kotlinx.coroutines.flow.Flow

interface CoroutinePresenter<ViewEvent, ViewModel> {
  suspend fun produceModels(events: Flow<ViewEvent>, models: ModelReceiver<ViewModel>)
}

// TODO(jwilson): defining this as an anonymous function crashed the compiler?
interface ModelReceiver<ViewModel> {
  suspend fun invoke(model: ViewModel)
}
