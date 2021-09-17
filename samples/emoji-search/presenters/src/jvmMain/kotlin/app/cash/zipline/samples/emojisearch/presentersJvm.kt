package app.cash.zipline.samples.emojisearch

import app.cash.zipline.Zipline
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.EmptySerializersModule

val Zipline.emojiSearchPresenter: EmojiSearchPresenter
  @OptIn(ExperimentalSerializationApi::class)
  get() {
    return get(
      name = "emojiSearchPresenter",
      serializersModule = EmptySerializersModule,
    )
  }
