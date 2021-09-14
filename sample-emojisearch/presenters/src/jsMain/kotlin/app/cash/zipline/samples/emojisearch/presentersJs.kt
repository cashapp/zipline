package app.cash.zipline.samples.emojisearch

import app.cash.zipline.Zipline
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.EmptySerializersModule

@OptIn(ExperimentalSerializationApi::class)
@JsExport
fun preparePresenters() {
  Zipline.set<CoroutinePresenter<EmojiSearchEvent, EmojiSearchViewModel>>(
    name = "emojiSearchPresenter",
    serializersModule = EmptySerializersModule,
    instance = EmojiSearchPresenter()
  )
}
