package app.cash.zipline.samples.emojisearch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

class EmojiSearchPresenter : CoroutinePresenter<EmojiSearchEvent, EmojiSearchViewModel> {
  override suspend fun produceModels(
    events: Flow<EmojiSearchEvent>,
    models: ModelReceiver<EmojiSearchViewModel>
  ) {
    events.collectLatest { event ->
      when (event) {
        is EmojiSearchEvent.SearchTermEvent -> {
          val filteredImages = sampleImages.filter { event.searchTerm in it.label }
          models.invoke(EmojiSearchViewModel(event.searchTerm, filteredImages))
        }
      }
    }
  }
}
