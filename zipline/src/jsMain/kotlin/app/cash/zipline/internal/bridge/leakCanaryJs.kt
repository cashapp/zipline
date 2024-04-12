/*
 * Copyright (C) 2022 Block, Inc.
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
package app.cash.zipline.internal.bridge

import app.cash.zipline.ZiplineService

private val referenceQueue: dynamic = js("""[]""")

internal val registry = run {
  @Suppress("UNUSED_VARIABLE") // `referenceQueue` is used by the js() block below.
  val referenceQueue = referenceQueue
  js(
    """
    new FinalizationRegistry(function(heldValue) {
      referenceQueue.push(heldValue);
    })
    """,
  )
}

internal actual fun trackLeaks(
  endpoint: Endpoint,
  serviceName: String,
  callHandler: OutboundCallHandler,
  service: ZiplineService,
) {
  registry.register(service, ZiplineServiceReference(endpoint, serviceName, callHandler))
}

internal actual fun detectLeaks() {
  while (true) {
    val reference = referenceQueue.shift() ?: break
    (reference as ZiplineServiceReference).afterGc()
  }
}

private class ZiplineServiceReference(
  private val endpoint: Endpoint,
  private val name: String,
  private val callHandler: OutboundCallHandler,
) {
  fun afterGc() {
    if (!callHandler.serviceState.closed) {
      endpoint.eventListener.serviceLeaked(name)
    }
  }
}
