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
package app.cash.zipline.chasm

import io.github.charlietap.chasm.embedding.function
import io.github.charlietap.chasm.embedding.shapes.HostFunction
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.runtime.value.NumberValue.I32
import io.github.charlietap.chasm.type.FunctionType
import io.github.charlietap.chasm.type.NumberType
import io.github.charlietap.chasm.type.ResultType
import io.github.charlietap.chasm.type.ValueType
import okio.ByteString
import okio.buffer

abstract class ZiplineWasmEnvironment {
  abstract fun randomGet(a: I32, b: I32): I32
  abstract fun fdWrite(fileDescriptor: I32, data: ByteString)

  fun imports(store: Store): List<Import> = ZiplineWasmEnvironmentAdapter(this, store).imports
}

private class ZiplineWasmEnvironmentAdapter(
  private val delegate: ZiplineWasmEnvironment,
  private val store: Store,
) {
  val imports: List<Import>
    get() = listOf(
      randomGet(),
      fdWrite(),
    )

  private fun randomGet(): Import {
    val functionType = FunctionType(
      params = ResultType(
        listOf(
          ValueType.Number(NumberType.I32),
          ValueType.Number(NumberType.I32),
        )
      ),
      results = ResultType(listOf(ValueType.Number(NumberType.I32))),
    )

    val randomGet: HostFunction = { list ->
      listOf(delegate.randomGet(list[0] as I32, list[1] as I32))
    }

    return Import(
      "wasi_snapshot_preview1",
      "random_get",
      function(store, functionType, randomGet),
    )
  }

  private fun fdWrite(): Import {
    val functionType = FunctionType(
      params = ResultType(
        listOf(
          ValueType.Number(NumberType.I32),
          ValueType.Number(NumberType.I32),
          ValueType.Number(NumberType.I32),
          ValueType.Number(NumberType.I32),
        )
      ),
      results = ResultType(listOf(ValueType.Number(NumberType.I32))),
    )

    val hostFunction: HostFunction = { list ->
      val fileDescriptor = list[0] as I32
      val iovecPointer = list[1] as I32
      val iovecSize = list[2] as I32
      val resultPointer = list[3] as I32

      val iovecSource = MemorySource(store, memory, iovecPointer.value).buffer()
      for (i in 0 until iovecSize.value) {
        val dataPointer = iovecSource.readIntLe()
        val dataSize = iovecSource.readIntLe()
        val dataSource = MemorySource(store, memory, dataPointer).buffer()
        val content = dataSource.readByteString(dataSize.toLong())

        delegate.fdWrite(fileDescriptor, content)

        val resultSink = MemorySink(store, memory, resultPointer.value).buffer()
        resultSink.writeIntLe(dataSize)
        resultSink.close()
      }

      listOf(0.i32)
    }

    return Import(
      "wasi_snapshot_preview1",
      "fd_write",
      function(store, functionType, hostFunction),
    )
  }
}

