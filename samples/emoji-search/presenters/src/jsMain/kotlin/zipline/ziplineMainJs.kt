package zipline

import app.cash.zipline.samples.emojisearch.preparePresenters

@OptIn(ExperimentalJsExport::class)
@JsExport
fun ziplineMain() {
  preparePresenters()
}
