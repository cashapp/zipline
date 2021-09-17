package app.cash.zipline.samples.emojisearch

import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.*

class RealHostApi(val dispatcher: CoroutineDispatcher) : HostApi {
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
          CoroutineScope(EmptyCoroutineContext).launch(dispatcher) {
            continuation.resumeWith(Result.failure(e))
          }
        }

        override fun onResponse(call: Call, response: Response) {
          val responseBytes = response.body!!.bytes()
          CoroutineScope(EmptyCoroutineContext).launch(dispatcher) {
            continuation.resumeWith(Result.success(responseBytes))
          }
        }
      })
    }
  }
}
