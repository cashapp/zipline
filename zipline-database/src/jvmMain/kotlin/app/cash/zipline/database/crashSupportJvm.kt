package app.cash.zipline.database

@Suppress("unused")
fun crashInit(handler: (t: Throwable) -> Unit) {
  crashHandler = handler
}

private var crashHandler: (t: Throwable) -> Unit = {}

/**
 * Enable handled reports from common code
 */
internal actual fun reportCrash(t: Throwable) {
  crashHandler(t)
}
