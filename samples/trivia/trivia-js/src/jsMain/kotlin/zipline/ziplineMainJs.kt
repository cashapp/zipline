package zipline

import app.cash.zipline.samples.trivia.launchZipline
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalJsExport::class)
@JsExport
fun main() {
  launchZipline()
}
