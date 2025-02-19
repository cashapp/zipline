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

class WasmFunction private constructor(
  private val pointer: Long,
  private val paramTypes: Array<WasmValueType>,
  private val resultTypes: Array<WasmValueType>,
) {
  fun call(args: List<WasmValue>): List<WasmValue> {
    val result = call(args.toTypedArray())
    return result.toList()
  }

  /*
  TODO.
  wasm_runtime_call_wasm_a(wasm_exec_env_t exec_env,
                           wasm_function_inst_t function, uint32_t num_results,
                           wasm_val_t results[], uint32_t num_args,
                           wasm_val_t *args);

     */
  external fun call(args: Array<WasmValue>): Array<WasmValue>
}
