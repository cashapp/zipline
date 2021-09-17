package app.cash.zipline.samples.emojisearch

import app.cash.zipline.Zipline
import java.io.Closeable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.modules.EmptySerializersModule
import okhttp3.*

//@NoLiveLiterals
class EmojiSearchZipline(val dispatcher: CoroutineDispatcher) : Closeable {
  val zipline = Zipline.create(dispatcher)

  init {
    val presentersJs = fetchCodeAsStream("http://10.0.2.2:8080/presenters.js")
    zipline.quickJs.evaluate(presentersJs, "presenters.js")
    zipline.set<HostApi>("hostApi", EmptySerializersModule, RealHostApi(dispatcher))
    zipline.quickJs.evaluate("presenters.app.cash.zipline.samples.emojisearch.preparePresenters()")
  }

  override fun close() {
//    ziplineExecutor.shutdown()
    zipline.close()
  }

  private fun fetchCodeAsStream(url: String): String {

    // NetworkOnMainThreadException

    val client = OkHttpClient()
    val call = client.newCall(
      Request.Builder()
        .url(url)
        .build()
    )
    val response = call.execute()
    return response.body!!.string()
  }
}
