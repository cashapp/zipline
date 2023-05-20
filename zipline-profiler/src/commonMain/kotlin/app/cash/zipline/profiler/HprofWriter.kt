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
package app.cash.zipline.profiler

import okio.Buffer
import okio.BufferedSink
import okio.ByteString
import okio.Closeable
import okio.utf8Size

/**
 * Creates binary HPROF files which is a widely-supported way to represent CPU samples in the JVM
 * ecosystem. Documentation for this format is limited, particularly since it was dropped as a
 * built-in format in the JVM.
 *
 * - https://java.net/downloads/heap-snapshot/hprof-binary-format.html
 * - https://hg.openjdk.java.net/jdk6/jdk6/jdk/raw-file/tip/src/share/demo/jvmti/hprof/manual.html
 * - https://github.com/eaftan/hprof-parser
 */
internal class HprofWriter(
  private val sink: BufferedSink,
  private val clock: ProfilerClock = DefaultProfilerClock,
) : Closeable by sink {
  private val headerNanoTime = clock.nanoTime
  private val elapsedMicros: Int
    get() = ((clock.nanoTime - headerNanoTime) / 1_000).toInt()

  private val stringIds = mutableMapOf<String, Int>()
  private val classIds = mutableMapOf<String, Int>()
  private val stackFrameIds = mutableMapOf<ByteString, Int>()
  private val stackTraceIds = mutableMapOf<ByteString, Int>()
  private val cpuSamples = mutableMapOf<Int, MutableInt>()

  fun writeHeaderFrame() {
    sink.writeUtf8("JAVA PROFILE 1.0.1\u0000") // standard header.
    sink.writeInt(4) // size of identifiers.

    // We don't bother to get the current time because it's slightly difficult in multiplatform.
    val headerTime = 0L
    sink.writeInt((headerTime shr 32).toInt())
    sink.writeInt(headerTime.toInt())
  }

  fun writeControlSettings(
    allocationTraces: Boolean = false,
    cpuSampling: Boolean = false,
    stackTraceDepth: Short = Short.MAX_VALUE,
  ) {
    var bitMaskFlags = 0
    if (allocationTraces) bitMaskFlags = bitMaskFlags or 0x1
    if (cpuSampling) bitMaskFlags = bitMaskFlags or 0x2

    sink.writeByte(0x0e) // Tag.
    sink.writeInt(elapsedMicros)
    sink.writeInt(6) // Record length.
    sink.writeInt(bitMaskFlags)
    sink.writeShort(stackTraceDepth.toInt())
  }

  fun writeThreadStart(
    threadId: Int,
    threadObjectId: Int,
    stackTraceId: Int,
    threadNameStringId: Int,
    threadGroupNameId: Int,
    threadParentGroupNameId: Int,
  ) {
    sink.writeByte(0x0a) // Tag.
    sink.writeInt(elapsedMicros)
    sink.writeInt(24) // Record length.
    sink.writeInt(threadId)
    sink.writeInt(threadObjectId)
    sink.writeInt(stackTraceId)
    sink.writeInt(threadNameStringId)
    sink.writeInt(threadGroupNameId)
    sink.writeInt(threadParentGroupNameId)
  }

  fun writeThreadEnd(
    threadId: Int,
  ) {
    sink.writeByte(0x0b) // Tag.
    sink.writeInt(elapsedMicros)
    sink.writeInt(4) // Record length.
    sink.writeInt(threadId)
  }

  /** This has the side effect of writing a frame if a string ID hasn't been assigned. */
  fun allocateStringId(string: String): Int {
    return stringIds.getOrPut(string) {
      val id = stringIds.size + 1_000_000
      sink.writeByte(0x01) // Tag.
      sink.writeInt(elapsedMicros)
      sink.writeInt(4 + string.utf8Size().toInt()) // Record length.
      sink.writeInt(id)
      sink.writeUtf8(string)
      return@getOrPut id
    }
  }

  /** This has the side effect of writing a frame if a serial number hasn't been assigned. */
  fun allocateClassId(
    className: String,
    classObjectId: Int,
    stackTraceSerialNumber: Int,
  ): Int {
    return classIds.getOrPut(className) {
      val id = classIds.size + 2_000_000
      val classNameStringId = allocateStringId(className)
      sink.writeByte(0x02) // Tag.
      sink.writeInt(elapsedMicros)
      sink.writeInt(16) // Record length.
      sink.writeInt(id)
      sink.writeInt(classObjectId)
      sink.writeInt(stackTraceSerialNumber)
      sink.writeInt(classNameStringId)
      return@getOrPut id
    }
  }

  fun allocateStackFrameId(
    methodNameStringId: Int,
    methodSignatureStringId: Int,
    sourceFileNameStringId: Int,
    classId: Int,
    lineNumber: Int,
  ): Int {
    val stackFrame = run {
      val buffer = Buffer()
      buffer.writeInt(methodNameStringId)
      buffer.writeInt(methodSignatureStringId)
      buffer.writeInt(sourceFileNameStringId)
      buffer.writeInt(classId)
      buffer.writeInt(lineNumber)
      buffer.readByteString()
    }

    return stackFrameIds.getOrPut(stackFrame) {
      val id = stackFrameIds.size + 3_000_000
      sink.writeByte(0x04) // Tag.
      sink.writeInt(elapsedMicros)
      sink.writeInt(24) // Record length.
      sink.writeInt(id)
      sink.write(stackFrame)
      return@getOrPut id
    }
  }

  fun allocateStackTraceId(stackFrameIds: ByteString): Int {
    return stackTraceIds.getOrPut(stackFrameIds) {
      val id = stackTraceIds.size + 4_000_000
      sink.writeByte(0x05) // Tag.
      sink.writeInt(elapsedMicros)
      sink.writeInt(12 + stackFrameIds.size) // Record length.
      sink.writeInt(id)
      sink.writeInt(0) // Thread ID.
      sink.writeInt(stackFrameIds.size / 4)
      sink.write(stackFrameIds)
      return@getOrPut id
    }
  }

  fun addCpuSample(stackTraceId: Int) {
    cpuSamples.getOrPut(stackTraceId) { MutableInt() }.value++
  }

  fun writeCpuSamples() {
    sink.writeByte(0x0d) // Tag.
    sink.writeInt(elapsedMicros)
    sink.writeInt(8 + 8 * cpuSamples.size) // Record length.
    sink.writeInt(cpuSamples.values.sumOf { it.value }) // Total samples.
    sink.writeInt(cpuSamples.size) // Number of traces.
    for ((stackTraceId, sampleCount) in cpuSamples) {
      sink.writeInt(sampleCount.value)
      sink.writeInt(stackTraceId)
    }
  }

  private class MutableInt {
    var value = 0
  }
}
