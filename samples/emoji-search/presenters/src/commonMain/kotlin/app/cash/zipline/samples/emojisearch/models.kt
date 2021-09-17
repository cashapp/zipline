package app.cash.zipline.samples.emojisearch

import kotlinx.serialization.Serializable

@Serializable
sealed class EmojiSearchEvent {
  @Serializable
  class SearchTermEvent(
    val searchTerm: String
  ) : EmojiSearchEvent()
}

@Serializable
data class EmojiSearchViewModel(
  val searchTerm: String,
  val images: List<EmojiImage>
)

@Serializable
data class EmojiImage(
  val label: String,
  val url: String
)
