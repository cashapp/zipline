package app.cash.zipline.testing

import app.cash.zipline.Zipline
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class JsSuspendingPotatoService(
  private val greeting: String,
) : SuspendingPotatoService {
  override suspend fun echo(): EchoResponse {
    return EchoResponse("$greeting from suspending JavaScript, anonymous")
  }
}

private val zipline by lazy { Zipline.get() }

@JsExport
fun prepareSuspendingPotatoJsBridges() {
  zipline.bind<SuspendingPotatoService>(
    "jsSuspendingPotatoService",
    JsSuspendingPotatoService("hello"),
  )
}

@JsExport
fun callSuspendingPotatoService() {
  val service = zipline.take<SuspendingPotatoService>("jvmSuspendingPotatoService")
  GlobalScope.launch(start = CoroutineStart.UNDISPATCHED) {
    try {
      suspendingPotatoResult = service.echo().message
    } catch (e: Exception) {
      suspendingPotatoException = e.stackTraceToString()
    }
  }
}

@JsExport
var suspendingPotatoResult: String? = null

@JsExport
var suspendingPotatoException: String? = null
