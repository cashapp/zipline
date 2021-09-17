package app.cash.zipline.samples.emojisearch

import app.cash.zipline.Zipline
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.EmptySerializersModule

@OptIn(ExperimentalSerializationApi::class)
@JsExport
fun preparePresenters() {
  val hostApi = Zipline.get<HostApi>(
    name = "hostApi",
    serializersModule = EmptySerializersModule
  )

  Zipline.set<EmojiSearchPresenter>(
    name = "emojiSearchPresenter",
    serializersModule = EmptySerializersModule,
    instance = RealEmojiSearchPresenter(hostApi)
  )
}
