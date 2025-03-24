/*
 * Copyright (C) 2025 Cash App
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

import app.cash.zipline.internal.GuestService
import app.cash.zipline.internal.bridge.CallChannel
import kotlin.wasm.unsafe.withScopedMemoryAllocator


@WasmExport
fun call(id: Int): Int {
  withScopedMemoryAllocator { allocator ->
    val size = receiveSize(id).toInt()
    val pointer = allocator.allocate(size)
    receiveData(id, pointer.address)
    return pointer.readByteArray(size)
  }
}

object WasmOutboundChannel : CallChannel {
  override fun call(callJson: String): String {
    withScopedMemoryAllocator { allocator ->
      val pointer = allocator.allocate(data.size)
      pointer.writeByteArray(data)
      return sendData(pointer.address, data.size.toUInt())
    }
  }

  override fun disconnect(instanceName: String): Boolean {
    TODO("Not yet implemented")
  }
}


object WasmInboundChannel : CallChannel {
  private val zipline: Zipline
    get() = theOnlyZipline ?: error("Zipline isn't ready: did you call Zipline.get() yet?")

  private val inboundChannel: CallChannel
    get() = zipline.endpoint.inboundChannel

  override fun call(callJson: String) = inboundChannel.call(callJson)

  override fun disconnect(instanceName: String) = inboundChannel.disconnect(instanceName)
}

internal object WasmGuestService : GuestService {
  private val zipline: Zipline
    get() = theOnlyZipline ?: error("Zipline isn't ready: did you call Zipline.get() yet?")

  override val serviceNames: Set<String>
    get() = zipline.endpoint.serviceNames

  override fun serviceType(name: String) = zipline.endpoint.serviceType(name)

  override fun runJob(timeoutId: Int) {
    TODO("Not yet implemented")
  }
}
