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

import app.cash.plywood.WasmExternal
import app.cash.plywood.WasmFunction
import app.cash.plywood.WasmModule
import app.cash.plywood.WasmValue
import app.cash.plywood.bridge.HostBridge

internal class ZiplineWasmBridge(
  private val hostBridge: HostBridge,
) {
  fun outboundCallChannel(module: WasmModule): CallChannel {
    val guestCall = module.function("guestCall")!!
    val guestDisconnect = module.function("guestDisconnect")!!

    return object : CallChannel {
      override fun call(callJson: String): String {
        val callJsonBytes = callJson.encodeToByteArray()
        val callJsonId = hostBridge.send(callJsonBytes)
        val guestCallResult = guestCall(listOf(WasmValue.I32(callJsonId.toInt())))
        val resultId = guestCallResult.single() as WasmValue.I32
        val resultBytes = hostBridge.receive(resultId.value.toUInt())
        return resultBytes.decodeToString()
      }

      override fun disconnect(instanceName: String): Boolean {
        val instanceNameBytes = instanceName.encodeToByteArray()
        val instanceNameId = hostBridge.send(instanceNameBytes).toInt()
        val result = guestDisconnect(listOf(WasmValue.I32(instanceNameId))).single() as WasmValue.I32
        return result.value == 0
      }
    }
  }

  fun imports(
    module: WasmModule,
    hostCallChannel: CallChannel,
  ): Array<WasmExternal> {
    val service = Service(hostBridge, hostCallChannel)
    return service.asImports(module.spec)
  }

  private inner class Service(
    val hostBridge: HostBridge,
    val inboundCallChannel: CallChannel,
  ) {
    fun hostCall(callJsonId: Int): Int {
      val callJsonBytes = hostBridge.receive(callJsonId.toUInt())
      val callJson = callJsonBytes.decodeToString()
      val result = inboundCallChannel.call(callJson)
      return hostBridge.send(result.encodeToByteArray()).toInt()
    }

    fun hostDisconnect(instanceNameId: Int): Int {
      val instanceNameBytes = hostBridge.receive(instanceNameId.toUInt())
      val instanceName = instanceNameBytes.decodeToString()
      val success = inboundCallChannel.disconnect(instanceName)
      return if (success) 0 else 1
    }

    fun asImports(spec: WasmModule.Spec) = arrayOf<WasmExternal>(
      object : WasmFunction {
        override val spec = spec.imports.single { it.name == "hostCall" } as WasmFunction.Spec
        override fun invoke(args: List<WasmValue>): List<WasmValue> {
          val result = hostCall(
            (args[0] as WasmValue.I32).value,
          )
          return listOf(WasmValue.I32(result))
        }
      },
      object : WasmFunction {
        override val spec = spec.imports.single { it.name == "hostDisconnect" } as WasmFunction.Spec
        override fun invoke(args: List<WasmValue>): List<WasmValue> {
          val result = hostDisconnect(
            (args[0] as WasmValue.I32).value,
          )
          return listOf(WasmValue.I32(result))
        }
      },
    )
  }
}
