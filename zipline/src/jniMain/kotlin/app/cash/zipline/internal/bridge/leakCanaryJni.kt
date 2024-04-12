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
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.util.Collections.synchronizedSet

internal actual fun trackLeaks(
  endpoint: Endpoint,
  serviceName: String,
  callHandler: OutboundCallHandler,
  service: ZiplineService,
) {
  allReferencesSet += ZiplineServiceReference(endpoint, serviceName, callHandler, service)
}

internal actual fun detectLeaks() {
  while (true) {
    val reference = allReferencesQueue.poll() ?: break
    (reference as ZiplineServiceReference).afterGc()
  }
}

internal actual fun stopTrackingLeaks(
  endpoint: Endpoint,
) {
  allReferencesSet.removeAll { it.endpoint == endpoint }
}

/** Keep every [ZiplineServiceReference] reachable until its target is GC'd. */
private val allReferencesSet = synchronizedSet(mutableSetOf<ZiplineServiceReference>())

/** The VM adds each [ZiplineServiceReference] here when its target is GC'd. */
private val allReferencesQueue = ReferenceQueue<ZiplineService>()

private class ZiplineServiceReference(
  val endpoint: Endpoint,
  private val serviceName: String,
  private val callHandler: OutboundCallHandler,
  service: ZiplineService,
) : PhantomReference<ZiplineService>(service, allReferencesQueue) {
  fun afterGc() {
    allReferencesSet.remove(this)
    if (!callHandler.serviceState.closed) {
      endpoint.eventListener.serviceLeaked(serviceName)
    }
  }
}
