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
package app.cash.zipline.testing

import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.Endpoint
import kotlinx.coroutines.CoroutineDispatcher

/** Returns a pair of endpoints connected to each other for testing. */
internal fun newEndpointPair(dipatcher: CoroutineDispatcher): Pair<Endpoint, Endpoint> {
  val pair = object : Any() {
    val a: Endpoint = Endpoint(dipatcher, object : CallChannel {
      override fun serviceNamesArray(): Array<String> {
        return b.inboundChannel.serviceNamesArray()
      }

      override fun invoke(
        instanceName: String,
        funName: String,
        encodedArguments: ByteArray
      ): ByteArray {
        return b.inboundChannel.invoke(instanceName, funName, encodedArguments)
      }

      override fun invokeSuspending(
        instanceName: String,
        funName: String,
        encodedArguments: ByteArray,
        callbackName: String
      ) {
        return b.inboundChannel.invokeSuspending(
          instanceName, funName, encodedArguments, callbackName
        )
      }

      override fun disconnect(instanceName: String): Boolean {
        return b.inboundChannel.disconnect(instanceName)
      }
    })

    val b: Endpoint = Endpoint(dipatcher, a.inboundChannel)
  }

  return pair.a to pair.b
}
