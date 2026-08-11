/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(ExperimentalJsExport::class)

package app.cash.zipline.testing

import app.cash.zipline.Zipline
import app.cash.zipline.asDynamicSuspendingFunction
import app.cash.zipline.sourceType
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JsSuspendingEchoService(
  private val greeting: String,
) : SuspendingEchoService {
  override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
    mutex.withLock {
      return EchoResponse("$greeting from suspending JavaScript, ${request.message}")
    }
  }
}

private val zipline by lazy { Zipline.get() }

private val mutex = Mutex(locked = true)

@JsExport
fun prepareSuspendingJsBridges() {
  zipline.bind<SuspendingEchoService>(
    "jsSuspendingEchoService",
    JsSuspendingEchoService("hello"),
  )
}

@JsExport
fun unblockSuspendingJs() {
  mutex.unlock()
}

@OptIn(DelicateCoroutinesApi::class)
@JsExport
fun callSuspendingEchoService(message: String) {
  val service = zipline.take<SuspendingEchoService>("jvmSuspendingEchoService")
  GlobalScope.launch(start = CoroutineStart.UNDISPATCHED) {
    suspendingEchoResult = try {
      service.suspendingEcho(EchoRequest(message)).message
    } catch (e: Exception) {
      e.toString()
    }
  }
}

@OptIn(DelicateCoroutinesApi::class)
@JsExport
fun callSuspendingEchoServiceDynamically(message: String) {
  val service = zipline.take<SuspendingEchoService>("jvmSuspendingEchoService")
  val suspendingEcho = service.sourceType!!.functions.single { "suspendingEcho" in it.signature }
    .asDynamicSuspendingFunction()
  val request = js("""{"message":message}""")
  GlobalScope.launch(start = CoroutineStart.UNDISPATCHED) {
    suspendingEchoResult = try {
      suspendingEcho(service, arrayOf(request)).asDynamic().message
    } catch (e: Exception) {
      e.toString()
    }
  }
}

@JsExport
var suspendingEchoResult: String? = null

/**
 * Regression test service for a bug where an outbound Zipline call made from inside a
 * genuine native JS Promise's `.then()` reaction (as opposed to one made directly from
 * a Zipline inbound-call handler) never reached the host: QuickJS enqueues Promise
 * reactions as "jobs" that only run once the embedder explicitly drains them, and
 * nothing did. `Promise(...).await()` below is a real native Promise, not a Zipline RPC
 * call - its continuation (and the outbound call chained after it) only resumes if the
 * job queue gets drained.
 */
class JsSuspendingEchoServiceAfterRealPromise : SuspendingEchoService {
  override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
    val relayedMessage = Promise<String> { resolve, _ -> resolve(request.message) }.await()
    val service = zipline.take<SuspendingEchoService>("jvmSuspendingEchoService")
    return service.suspendingEcho(EchoRequest(relayedMessage))
  }
}

@JsExport
fun prepareSuspendingJsAfterRealPromiseBridge() {
  zipline.bind<SuspendingEchoService>(
    "jsSuspendingEchoServiceAfterRealPromise",
    JsSuspendingEchoServiceAfterRealPromise(),
  )
}
