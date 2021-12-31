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
package app.cash.zipline

import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.internal.bridge.InboundCallHandler
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter

abstract class ZiplineReference<T : ZiplineService> internal constructor() {
  abstract fun get(): T

  abstract fun close()
}

fun <T : ZiplineService> ZiplineReference(service: T): ZiplineReference<T> {
  error("unexpected call to ZiplineReference(): is the Zipline plugin configured?")
}

@PublishedApi
internal fun <T : ZiplineService> ZiplineReference(
  service: T,
  adapter: ZiplineServiceAdapter<T>
): ZiplineReference<T> = InboundZiplineReference(service, adapter)

// Plugin-rewritten code calls the constructor of this class.
@PublishedApi
internal class InboundZiplineReference<T : ZiplineService>(
  private val service: T,
  private val adapter: ZiplineServiceAdapter<T>,
): ZiplineReference<T>() {
  private var name: String? = null
  private var endpoint: Endpoint? = null

  internal fun connect(endpoint: Endpoint, name: String): InboundCallHandler {
    check(this.endpoint == null && this.name == null) { "already connected" }
    this.name = name
    this.endpoint = endpoint
    val context = endpoint.newInboundContext()
    val result = adapter.inboundCallHandler(service, context)
    endpoint.inboundHandlers[name] = result
    return result
  }

  override fun get(): T {
    return service
  }

  override fun close() {
    val endpoint = this.endpoint
    val name = this.name
    if (endpoint != null && name != null) {
      val removed = endpoint.remove(name)
      this.endpoint = null
      this.name = null
      require(removed != null) { "unable to find $name: was it removed twice?" }
    }
  }
}

internal class OutboundZiplineReference<T : ZiplineService>(
  private val adapter: ZiplineServiceAdapter<T>,
) : ZiplineReference<T>() {
  private var name: String? = null
  private var endpoint: Endpoint? = null

  internal fun connect(endpoint: Endpoint, name: String) {
    check(this.endpoint == null && this.name == null) { "already connected" }
    this.name = name
    this.endpoint = endpoint
  }

  override fun get(): T {
    val endpoint = this.endpoint ?: throw IllegalStateException("not connected")
    val context = endpoint.newOutboundContext(this.name!!)
    return adapter.outboundService(context)
  }

  override fun close() {
    val name = this.name
    val endpoint = this.endpoint
    if (name != null && endpoint != null) {
      this.name = null
      this.endpoint = null
      endpoint.outboundChannel.disconnect(name)
    }
  }
}
