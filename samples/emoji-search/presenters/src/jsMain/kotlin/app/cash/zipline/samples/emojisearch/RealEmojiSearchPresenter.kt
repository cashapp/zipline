package app.cash.zipline.samples.emojisearch

import app.cash.zipline.FlowReference
import app.cash.zipline.toFlowReference
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class RealEmojiSearchPresenter(
  val hostApi: HostApi
) : EmojiSearchPresenter {
  var imageIndex = listOf<EmojiImage>()
  var latestSearchTerm = ""

  suspend fun loadImageIndex() {
    val httpResponseBytes = hostApi.httpCall("https://api.github.com/emojis")
    val emojisJson = httpResponseBytes.decodeToString()
    val labelToUrl = Json.decodeFromString<Map<String, String>>(emojisJson)
    imageIndex = labelToUrl.map { (key, value) -> EmojiImage(key, value) }
  }

  override suspend fun produceModels(
    eventsReference: FlowReference<EmojiSearchEvent>
  ): FlowReference<EmojiSearchViewModel> {
    return coroutineScope {
      val flow: Flow<EmojiSearchViewModel> = flow {
        launch {
          println("LOADING IMAGE INDEX")
          loadImageIndex()
          println("LOADED IMAGE INDEX")
          publishModel(this@flow)
          println("IMAGE LOADING DONE DONE DONE")
        }

        val events = eventsReference.get(EmojiSearchEvent.serializer())
        events.collect { event ->
          console.error("COLLECTED EVENT!!!")
          when (event) {
            is EmojiSearchEvent.SearchTermEvent -> {
              latestSearchTerm = event.searchTerm
              publishModel(this@flow)
            }
          }
        }
        console.error("FLOW COMPLETE!")
      }
      return@coroutineScope flow.toFlowReference(EmojiSearchViewModel.serializer())
    }
  }

  private suspend fun publishModel(collector: FlowCollector<EmojiSearchViewModel>) {
    console.error("PUBLISHING MODEL!!!")
    val filteredImages = imageIndex.filter { latestSearchTerm in it.label }
    collector.emit(EmojiSearchViewModel(latestSearchTerm, filteredImages))
    console.error("DONE PUBLISHING MODEL!!!")
  }
}
