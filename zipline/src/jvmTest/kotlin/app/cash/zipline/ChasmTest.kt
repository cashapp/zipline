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

import assertk.assertThat
import assertk.assertions.containsExactly
import io.github.charlietap.chasm.embedding.function
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.HostFunction
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.runtime.value.NumberValue.I32
import io.github.charlietap.chasm.type.FunctionType
import io.github.charlietap.chasm.type.NumberType
import io.github.charlietap.chasm.type.ResultType
import io.github.charlietap.chasm.type.ValueType
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Test

class ChasmTest {

  val path = "/zipline-root-zipline-testing-wasm-wasm-wasi.wasm".toPath()

  @Test
  fun happyPath() {
    val wasmFileAsByteArray = FileSystem.RESOURCES.read(path) { readByteArray() }
    val module = module(wasmFileAsByteArray)
      .expect("module load failed")
    val store = store()

    println(module.imports)
    println(module.exports)

    val environment = object : ZiplineWasmEnvironment {
      override fun randomGet(a: I32, b: I32): I32 {
        return 5.i32
      }
    }

    val adapter = ZiplineWasmEnvironmentAdapter(
      environment, store,
    )

    val initialize = module.exports.single { it.name == "_initialize" }
    val sum = module.exports.single { it.name == "sum" }
    val memory = module.exports.single { it.name == "memory" }


    val moduleInstance = instance(
      store = store,
      module = module,
      imports = adapter.imports(),
    ).expect("module instance create failed")
    val results = invoke(store, moduleInstance, "sum", listOf(99.i32, 101.i32))
      .expect("invoke failed")
    assertThat(results).containsExactly(200.i32)

    val result = instance(
      store = store,
      module = module,
      imports = listOf(),
    )
  }

}

interface ZiplineWasmEnvironment {
  fun randomGet(a: I32, b: I32): I32
}

class ZiplineWasmEnvironmentAdapter(
  private val delegate: ZiplineWasmEnvironment,
  private val store: Store,
) {

  fun imports(): List<Import> {
    val functionType = FunctionType(
      params = ResultType(
        listOf(
          ValueType.Number(NumberType.I32),
          ValueType.Number(NumberType.I32)
        )
      ),
      results = ResultType(listOf(ValueType.Number(NumberType.I32))),
    )

    val randomGet: HostFunction = { list ->
      listOf(delegate.randomGet(list[0] as I32, list[1] as I32))
    }

    return listOf(
      Import(
        "wasi_snapshot_preview1",
        "random_get",
        function(store, functionType, randomGet),
      )
    )
  }
}

private val Int.i32
  get() = I32(this)
