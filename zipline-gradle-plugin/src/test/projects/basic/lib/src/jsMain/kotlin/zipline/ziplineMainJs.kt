package zipline

@OptIn(ExperimentalJsExport::class)
@JsExport
fun ziplineMain() {
  app.cash.zipline.tests.launchGreetService()
}
