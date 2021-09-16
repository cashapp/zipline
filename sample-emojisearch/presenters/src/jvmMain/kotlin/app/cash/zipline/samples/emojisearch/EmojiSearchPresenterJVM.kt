package app.cash.zipline.samples.emojisearch

import java.io.IOException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.*

class EmojiSearchPresenterJVM(
  val hostApi: HostApi
) : CoroutinePresenter<EmojiSearchEvent, EmojiSearchViewModel> {
  var imageIndex = listOf<EmojiImage>()
  var latestSearchTerm = ""

  suspend fun loadImageIndex() {
    val httpResponseBytes = hostApi.httpCall("https://api.github.com/emojis")
    val emojisJson = httpResponseBytes.decodeToString()
    val labelToUrl = Json.decodeFromString<Map<String, String>>(emojisJson)
    imageIndex = labelToUrl.map { (key, value) -> EmojiImage(key, value) }
  }

  override suspend fun produceModels(
    events: Flow<EmojiSearchEvent>,
    models: ModelReceiver<EmojiSearchViewModel>
  ) {
    coroutineScope {
      launch {
        loadImageIndex()
        publishModel(models)
      }

      events.collectLatest { event ->
        when (event) {
          is EmojiSearchEvent.SearchTermEvent -> {
            latestSearchTerm = event.searchTerm
            publishModel(models)
          }
        }
      }
    }
  }

  private suspend fun publishModel(models: ModelReceiver<EmojiSearchViewModel>) {
    val filteredImages = imageIndex.filter { latestSearchTerm in it.label }
    models.invoke(EmojiSearchViewModel(latestSearchTerm, filteredImages))
  }
}

class RealHostApi : HostApi {
  private val client = OkHttpClient()
  override suspend fun httpCall(url: String): ByteArray {
    return suspendCoroutine { continuation ->
      val call = client.newCall(
        Request.Builder()
          .url(url)
          .header("Accept", "application/vnd.github.v3+json")
          .build()
      )
      call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
          continuation.resumeWith(Result.success(response.body!!.bytes()))
        }
      })
    }
  }
}
