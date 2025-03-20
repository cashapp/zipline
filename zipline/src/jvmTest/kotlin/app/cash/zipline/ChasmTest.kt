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
@file:Suppress(
  "CANNOT_OVERRIDE_INVISIBLE_MEMBER",
  "INVISIBLE_MEMBER",
  "INVISIBLE_REFERENCE",
)

package app.cash.zipline

import assertk.assertThat
import assertk.assertions.containsExactly
import io.github.charlietap.chasm.embedding.function
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.memory.readBytes
import io.github.charlietap.chasm.embedding.memory.writeBytes
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.HostFunction
import io.github.charlietap.chasm.embedding.shapes.HostFunctionContext
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Memory
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.runtime.value.NumberValue.I32
import io.github.charlietap.chasm.type.FunctionType
import io.github.charlietap.chasm.type.NumberType
import io.github.charlietap.chasm.type.ResultType
import io.github.charlietap.chasm.type.ValueType
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer
import okio.use
import org.junit.Test

class ChasmTest {

  val path = "/zipline-root-zipline-testing-wasm-wasm-wasi.wasm".toPath()

  @Test
  fun happyPath() {
    val wasmFileAsByteArray = FileSystem.RESOURCES.read(path) { readByteArray() }
    val module = module(wasmFileAsByteArray)
      .expect("module load failed")
    val store = store()

    println("IMPORTS")
    for (import in module.imports) {
      println(import)
    }
    println("EXPORTS")
    for (export in module.exports) {
      println(export)
    }

    val environment = object : ZiplineWasmEnvironment {
      override fun randomGet(a: I32, b: I32): I32 {
        return 5.i32
      }

      override fun fdWrite(store: Store, memory: Memory, a: I32, b: I32, c: I32, d: I32): I32 {
        val iovecSource = MemorySource(store, memory, b.value).buffer()
        val dataPointer = iovecSource.readIntLe()
        val dataSize = iovecSource.readIntLe()

        val dataSource = MemorySource(store, memory, dataPointer).buffer()
        val content = dataSource.readByteArray(dataSize.toLong())

        println(content.decodeToString())

        val resultSink = MemorySink(store, memory, d.value).buffer()
        resultSink.writeIntLe(dataSize)
        resultSink.close()

        return 0.i32
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

    invoke(store, moduleInstance, "_initialize", listOf())
      .expect("initialize failed")

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
  fun fdWrite(store: Store, memory: Memory, a: I32, b: I32, c: I32, d: I32): I32
}

class ZiplineWasmEnvironmentAdapter(
  private val delegate: ZiplineWasmEnvironment,
  private val store: Store,
) {

  fun imports(): List<Import> {
    return listOf(
      randomGetImport(),
      fdWriteImport(),
    )
  }

  private fun fdWriteImport(): Import {
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

    val randomGet: HostFunction = { list ->
      listOf(
        delegate.fdWrite(
          store,
          memory,
          list[0] as I32, list[1] as I32, list[2] as I32, list[3] as I32
        )
      )
    }

    return Import(
      "wasi_snapshot_preview1",
      "fd_write",
      function(store, functionType, randomGet),
    )
  }

  private val HostFunctionContext.memory: Memory
    get() = instance.exports.single { it.name == "memory" }.value as Memory

  private fun randomGetImport(): Import {
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
}

private val Int.i32
  get() = I32(this)

class MemorySource(
  private val store: Store,
  private val memory: Memory,
  private var memoryPointer: Int = 0,
) : Source {
  private val cursor = Buffer.UnsafeCursor()

  override fun read(sink: Buffer, byteCount: Long): Long {
    val memorySize = 64 * 1024 * 1024 // nope
    sink.readAndWriteUnsafe(cursor).use {
      cursor.seek(sink.size)
      val sinkLimit = cursor.expandBuffer(1)
      val bytesToRead = minOf(sinkLimit.toInt(), byteCount.toInt(), memorySize - memoryPointer)
      readBytes(store, memory, cursor.data!!, memoryPointer, bytesToRead, cursor.start)
      memoryPointer += bytesToRead
      return bytesToRead.toLong()
    }
  }

  override fun close() {
  }

  override fun timeout() = Timeout.NONE
}

class MemorySink(
  private val store: Store,
  private val memory: Memory,
  private var memoryPointer: Int = 0,
) : Sink {
  override fun write(source: Buffer, byteCount: Long) {
    val data = source.readByteArray(byteCount)
    writeBytes(store, memory, memoryPointer, data)
  }

  override fun flush() {
  }

  override fun close() {
  }

  override fun timeout() = Timeout.NONE
}

