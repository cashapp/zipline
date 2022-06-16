package app.cash.zipline.database

import co.touchlab.crashkios.CrashHandler
import co.touchlab.crashkios.DefaultCrashHandler
import co.touchlab.crashkios.setupCrashHandler

/**
 * Call from Swift to set up a crash handler
 */
@Suppress("unused")
fun crashInit(handler: CrashHandler) {
  setupCrashHandler(handler)
}

@Suppress("unused")
fun stackTraceString(t: Throwable): String = t.getStackTrace().joinToString("\n")

/**
 * Enable handled reports from common code
 */
internal actual fun reportCrash(t: Throwable) {
  DefaultCrashHandler.myHandler().crash(t)
}
