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
package app.cash.zipline.internal

import app.cash.plywood.WasmExternal
import app.cash.plywood.WasmFunction
import app.cash.plywood.WasmMemory
import app.cash.plywood.WasmModule
import app.cash.plywood.WasmValue

class FakeKotlinStdlib {
  fun imports(module: WasmModule): Array<WasmExternal> {
    val service = Service(
      memory = module.memory("memory")!!,
    )
    return service.asImports(module.spec)
  }

  private inner class Service(
    val memory: WasmMemory,
  ) {
    fun random_get(a: Int, b: Int): Int {
      return 0
    }

    fun poll_oneoff(a: Int, b: Int, c: Int, d: Int): Int {
      return 0
    }

    fun clock_time_get(a: Int, b: Long, c: Int): Int {
      return 0
    }

    fun proc_exit(a: Int) {
      println()
    }

    fun fd_write(a: Int, b: Int, c: Int, d: Int): Int {
      return 0
    }

    fun asImports(spec: WasmModule.Spec) = arrayOf<WasmExternal>(
      object : WasmFunction {
        override val spec = spec.imports.single { it.name == "random_get" } as WasmFunction.Spec
        override fun invoke(args: List<WasmValue>): List<WasmValue> {
          val result = random_get(
            (args[0] as WasmValue.I32).value,
            (args[1] as WasmValue.I32).value,
          )
          return listOf(WasmValue.I32(result))
        }
      },
      object : WasmFunction {
        override val spec = spec.imports.single { it.name == "poll_oneoff" } as WasmFunction.Spec
        override fun invoke(args: List<WasmValue>): List<WasmValue> {
          val result = poll_oneoff(
            (args[0] as WasmValue.I32).value,
            (args[1] as WasmValue.I32).value,
            (args[2] as WasmValue.I32).value,
            (args[3] as WasmValue.I32).value,
          )
          return listOf(WasmValue.I32(result))
        }
      },
      object : WasmFunction {
        override val spec = spec.imports.single { it.name == "clock_time_get" } as WasmFunction.Spec
        override fun invoke(args: List<WasmValue>): List<WasmValue> {
          val result = clock_time_get(
            (args[0] as WasmValue.I32).value,
            (args[1] as WasmValue.I64).value,
            (args[2] as WasmValue.I32).value,
          )
          return listOf(WasmValue.I32(result))
        }
      },
      object : WasmFunction {
        override val spec = spec.imports.single { it.name == "proc_exit" } as WasmFunction.Spec
        override fun invoke(args: List<WasmValue>): List<WasmValue> {
          proc_exit(
            (args[0] as WasmValue.I32).value,
          )
          return listOf()
        }
      },
      object : WasmFunction {
        override val spec = spec.imports.single { it.name == "fd_write" } as WasmFunction.Spec
        override fun invoke(args: List<WasmValue>): List<WasmValue> {
          val result = fd_write(
            (args[0] as WasmValue.I32).value,
            (args[1] as WasmValue.I32).value,
            (args[2] as WasmValue.I32).value,
            (args[3] as WasmValue.I32).value,
          )
          return listOf(WasmValue.I32(result))
        }
      },
    )
  }
}
