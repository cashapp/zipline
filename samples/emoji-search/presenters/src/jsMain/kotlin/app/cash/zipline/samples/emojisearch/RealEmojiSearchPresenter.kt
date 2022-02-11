package app.cash.zipline.samples.emojisearch

import app.cash.zipline.FlowReference
import app.cash.zipline.asFlowReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@ExperimentalCoroutinesApi
class RealEmojiSearchPresenter(
  private val hostApi: HostApi
) : EmojiSearchPresenter {
  private var imageIndex = listOf<EmojiImage>()
  private var latestSearchTerm = ""

  private val loadingImage = EmojiImage(
    "watch",
    "https://github.githubassets.com/images/icons/emoji/unicode/231a.png?v8"
  )

  override suspend fun produceModels(
    eventsReference: FlowReference<EmojiSearchEvent>
  ): FlowReference<EmojiSearchViewModel> {
    return coroutineScope {
      val flow: Flow<EmojiSearchViewModel> = channelFlow {
        send(EmojiSearchViewModel("", listOf(loadingImage)))

        loadImageIndex()
        send(produceModel())

        val events = eventsReference.take()
        events.collectLatest { event ->
          when (event) {
            is EmojiSearchEvent.SearchTermEvent -> {
              latestSearchTerm = event.searchTerm
              send(produceModel())
            }
          }
        }
      }
      flow.asFlowReference()
    }
  }

  private suspend fun loadImageIndex() {
    val emojisJson = hostApi.httpCall(
      url = "https://api.github.com/emojis",
      headers = mapOf("Accept" to "application/vnd.github.v3+json")
    )
    val labelToUrl = Json.decodeFromString<Map<String, String>>(emojisJson)
    imageIndex = labelToUrl.map { (key, value) -> EmojiImage(key, value) }
  }

  private fun produceModel(): EmojiSearchViewModel {
    val filteredImages = imageIndex
      .filter { image ->
        latestSearchTerm.split(" ").all { it in image.label }
      }
      .take(25)
    return EmojiSearchViewModel(latestSearchTerm, filteredImages)
  }
}
