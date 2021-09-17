package app.cash.zipline.samples.emojisearch

import app.cash.zipline.FlowReference

interface EmojiSearchPresenter {
  suspend fun produceModels(events: FlowReference<EmojiSearchEvent>): FlowReference<EmojiSearchViewModel>
}
