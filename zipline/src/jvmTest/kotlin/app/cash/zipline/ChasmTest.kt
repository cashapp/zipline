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

import app.cash.zipline.chasm.i32
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.embedding.store
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Test

class ChasmTest {
  private val wasmEnvironment = FakeZiplineWasmEnvironment()
  private val path = "/zipline-root-zipline-testing-wasm-wasm-wasi.wasm".toPath()

  @Test
  fun happyPath() {
    val store = store()
    val module = module(FileSystem.RESOURCES.read(path) { readByteArray() })
      .expect("module load failed")


    val moduleInstance = instance(
      store = store,
      module = module,
      imports = wasmEnvironment.imports(store),
    ).expect("module instance create failed")

    invoke(store, moduleInstance, "_initialize", listOf())
      .expect("initialize failed")

    val results = invoke(store, moduleInstance, "sum", listOf(99.i32, 101.i32))
      .expect("invoke failed")
    assertThat(results).containsExactly(200.i32)
    assertThat(wasmEnvironment.stdout.readUtf8()).isEqualTo("I am summing 99 and 101\n")
  }
}
