package app.cash.zipline.samples.emojisearch

import app.cash.zipline.Zipline
import java.io.BufferedReader
import java.io.Closeable
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.serialization.modules.EmptySerializersModule

class EmojiSearchZipline : Closeable {
  private val ziplineExecutor = Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "Zipline")
  }

  val zipline = Zipline.create(ziplineExecutor.asCoroutineDispatcher())

  init {
    val presentersJs = Zipline::class.java.getResourceAsStream("/presenters.js")!!
      .bufferedReader()
      .use(BufferedReader::readText)
    zipline.quickJs.evaluate(presentersJs, "presenters.js")
    zipline.set<HostApi>("hostApi", EmptySerializersModule, RealHostApi())
    zipline.quickJs.evaluate("presenters.app.cash.zipline.samples.emojisearch.preparePresenters()")
  }

  override fun close() {
    ziplineExecutor.shutdown()
    zipline.close()
  }
}
