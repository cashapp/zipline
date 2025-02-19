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

class WasmModule private constructor(
  /** A pointer to a `wasm_module_t`, or 0 after [close]. */
  private val pointer: Long,
  /** A pointer to a `jbyte*`, or 0 after [close]. */
  private val wasmDataBuf: Long,
  /** TODO: implement support for [WasmExport.Table], [WasmExport.Global], [WasmExport.Memory]. */
  private val exports: Array<WasmExport?>,
) : AutoCloseable {

  external fun createInstance(
    stackSize: Long = 64 * 1024,
    heapSize: Long = 64 * 1024,
  ): WasmModuleInstance

  external override fun close()
}
