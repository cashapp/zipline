package app.cash.zipline.samples.emojisearch

sealed class EmojiSearchEvent {
  class SearchTermEvent(
    val searchTerm: String
  ) : EmojiSearchEvent()
}

data class EmojiSearchViewModel(
  val searchTerm: String,
  val images: List<EmojiImage>
)

data class EmojiImage(
  val label: String,
  val url: String
)
