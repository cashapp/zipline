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
@file:Suppress(
  // Access :zipline internals.j
  "CANNOT_OVERRIDE_INVISIBLE_MEMBER",
  "EXPOSED_FUNCTION_RETURN_TYPE",
  "EXPOSED_PARAMETER_TYPE",
  "INVISIBLE_MEMBER",
  "INVISIBLE_REFERENCE",
)

package app.cash.zipline.testing

import app.cash.zipline.internal.EndpointService
import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.internal.bridge.SerializableZiplineServiceType
import kotlin.jvm.JvmOverloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/** Returns a pair of endpoints connected to each other for testing. */
@JvmOverloads
fun newEndpointPair(
  scope: CoroutineScope,
  serializersModule: SerializersModule = EmptySerializersModule(),
  listenerA: Endpoint.EventListener = Endpoint.EventListener(),
  listenerB: Endpoint.EventListener = Endpoint.EventListener(),
): Pair<Endpoint, Endpoint> {
  val pair = object : Any() {
    val aEndpointService = EndpointServiceProxy { a }
    val bEndpointService = EndpointServiceProxy { b }

    val a: Endpoint = Endpoint(
      scope = scope,
      userSerializersModule = serializersModule,
      eventListener = listenerA,
      outboundChannel = object : CallChannel {
        override fun call(callJson: String): String {
          return b.inboundChannel.call(callJson)
        }

        override fun disconnect(instanceName: String): Boolean {
          return b.inboundChannel.disconnect(instanceName)
        }
      },
      oppositeProvider = { bEndpointService },
    )

    val b: Endpoint = Endpoint(
      scope = scope,
      userSerializersModule = serializersModule,
      eventListener = listenerB,
      outboundChannel = a.inboundChannel,
      oppositeProvider = { aEndpointService },
    )
  }

  return pair.a to pair.b
}

/**
 * Forward calls to [delegate] until this is closed. Note that in practice the endpoint service
 * should never be closed.
 */
private class EndpointServiceProxy(
  val delegate: () -> EndpointService,
) : EndpointService {
  var closed = false
  override val serviceNames: Set<String>
    get() {
      require(!closed)
      return delegate().serviceNames
    }

  override fun serviceType(name: String): SerializableZiplineServiceType? {
    require(!closed)
    return delegate().serviceType(name)
  }

  override fun close() {
    closed = true
  }
}
