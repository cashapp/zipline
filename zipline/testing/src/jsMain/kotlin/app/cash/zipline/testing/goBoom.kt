package app.cash.zipline.testing

@JsExport
fun goBoom(countDown: Int) {
  require(countDown > 0) { "boom" }
  goBoom(countDown - 1)
}
