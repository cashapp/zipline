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
package app.cash.zipline.internal.bridge

import app.cash.plywood.bridge.GuestBridge
import app.cash.zipline.theOnlyZipline

@WasmImport(module = "zipline")
private external fun hostCall(callJsonId: UInt): UInt

@WasmImport(module = "zipline")
private external fun hostDisconnect(instanceNameId: UInt): UInt

@WasmExport
fun guestCall(callJsonId: Int): Int {
  val callJsonBytes: ByteArray = GuestBridge.receive(callJsonId.toUInt())
  val callJson = callJsonBytes.decodeToString()
  val resultJson = theOnlyZipline!!.endpoint.inboundChannel.call(callJson)
  return GuestBridge.send(resultJson.encodeToByteArray()).toInt()
}

@WasmExport
fun guestDisconnect(instanceNameId: Int): Int {
  val instanceNameBytes: ByteArray = GuestBridge.receive(instanceNameId.toUInt())
  val instanceName = instanceNameBytes.decodeToString()
  val success = theOnlyZipline!!.endpoint.inboundChannel.disconnect(instanceName)
  return if (success) 0 else 1
}

internal object GuestBridgeCallChannel : CallChannel {
  override fun call(callJson: String): String {
    val callJsonBytes = callJson.encodeToByteArray()
    val callJsonId = GuestBridge.send(callJsonBytes)
    val resultId = hostCall(callJsonId)
    val resultBytes = GuestBridge.receive(resultId)
    return resultBytes.decodeToString()
  }

  override fun disconnect(instanceName: String): Boolean {
    val instanceNameBytes = instanceName.encodeToByteArray()
    val instanceNameId = GuestBridge.send(instanceNameBytes)
    val result = hostDisconnect(instanceNameId)
    return result == 0U
  }
}
