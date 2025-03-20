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

import io.github.charlietap.chasm.embedding.memory.readBytes
import io.github.charlietap.chasm.embedding.memory.writeBytes
import io.github.charlietap.chasm.embedding.shapes.HostFunctionContext
import io.github.charlietap.chasm.embedding.shapes.Memory
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.runtime.value.NumberValue.I32
import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout
import okio.use

internal class MemorySource(
  private val store: Store,
  private val memory: Memory,
  private var memoryPointer: Int = 0,
) : Source {
  private val cursor = Buffer.UnsafeCursor()

  override fun read(sink: Buffer, byteCount: Long): Long {
    val memorySize = 64 * 1024 * 1024 // TODO: Chasm API to read the real thing.
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

internal class MemorySink(
  private val store: Store,
  private val memory: Memory,
  private var memoryPointer: Int = 0,
) : Sink {
  override fun write(source: Buffer, byteCount: Long) {
    writeBytes(store, memory, memoryPointer, source.readByteArray(byteCount))
  }

  override fun flush() {
  }

  override fun close() {
  }

  override fun timeout() = Timeout.NONE
}

val Int.i32
  get() = I32(this)

internal val HostFunctionContext.memory: Memory
  get() = instance.exports.single { it.name == "memory" }.value as Memory
