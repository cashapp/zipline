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

import app.cash.zipline.EngineApi
import app.cash.zipline.InterruptHandler
import app.cash.zipline.QuickJs
import okio.Buffer
import okio.BufferedSink
import okio.Closeable
import okio.FileSystem
import okio.Path
import okio.buffer

/**
 * Starts collecting CPU samples and writing them to [hprofFile]. The caller must close the returned
 * object to stop collecting samples and finish writing the file.
 *
 * While sampling this replaces [QuickJs.interruptHandler] with one that captures the JavaScript
 * stack on each interrupt poll.
 *
 * @param hprofFile a new file to write profiling data to. Typically, such files end with `.hprof`.
 */
@EngineApi
fun QuickJs.startCpuSampling(fileSystem: FileSystem, hprofFile: Path): Closeable {
  val bufferedSink = fileSystem.sink(hprofFile).buffer()
  return startCpuSampling(bufferedSink)
}

@EngineApi
fun QuickJs.startCpuSampling(hprofSink: BufferedSink): Closeable {
  val samplingProfiler = SamplingProfiler(this, HprofWriter(hprofSink))
  val previousInterruptHandler = interruptHandler
  interruptHandler = samplingProfiler

  return object : Closeable {
    override fun close() {
      interruptHandler = previousInterruptHandler
      samplingProfiler.close()
    }
  }
}

internal class SamplingProfiler internal constructor(
  private val quickJs: QuickJs,
  private val hprofWriter: HprofWriter,
) : Closeable,
  InterruptHandler {
  private var nextId = 1

  /** Placeholder value for the JavaScript thread. */
  private val javaScriptThreadId: Int = nextId++

  /** Placeholder value for JavaScript functions that don't have a proper signature. */
  private val javaScriptMethodSignatureStringId: Int

  /** Placeholder value for JavaScript functions that don't have a proper class. */
  private val javaScriptClassId: Int

  init {
    hprofWriter.writeHeaderFrame()
    hprofWriter.writeControlSettings(cpuSampling = true)
    val nullStackTraceId = nextId++
    javaScriptMethodSignatureStringId = hprofWriter.allocateStringId("()V")
    javaScriptClassId = hprofWriter.allocateClassId(
      className = "JavaScript",
      classObjectId = nextId++,
      stackTraceSerialNumber = nullStackTraceId,
    )
    hprofWriter.writeThreadStart(
      threadId = javaScriptThreadId,
      threadObjectId = nextId++,
      stackTraceId = nullStackTraceId,
      threadNameStringId = hprofWriter.allocateStringId("main"),
      threadGroupNameId = hprofWriter.allocateStringId("main"),
      threadParentGroupNameId = hprofWriter.allocateStringId("system"),
    )
  }

  override fun poll(): Boolean {
    val stack = quickJs.evaluate("new Error().stack") as String
    addStacktraceSample(stack)
    return false
  }

  /**
   * The stack is a string containing `\n`-separated frames. Each looks like these:
   *
   * ```
   *     at <eval> (?)
   *     at fib20 (bigFibs.js:42)
   * ```
   *
   * In the 2nd example above, `fib20` is the function name, `bigFibs.js` is the file name, and
   * `42` is the line number.
   */
  private fun addStacktraceSample(stack: String) {
    val stackFrameIds = Buffer()
    for (matchResult in STACK_FRAME_REGEX.findAll(stack)) {
      val stackFrameId = allocateStackFrameId(matchResult)
      stackFrameIds.writeInt(stackFrameId)
    }
    val stackTraceId = hprofWriter.allocateStackTraceId(stackFrameIds.readByteString())
    hprofWriter.addCpuSample(stackTraceId)
  }

  private fun allocateStackFrameId(matchResult: MatchResult): Int {
    val (function, file, line) = matchResult.destructured
    val methodNameId = hprofWriter.allocateStringId(function)
    val sourceFileNameStringId = hprofWriter.allocateStringId(file)
    return hprofWriter.allocateStackFrameId(
      methodNameStringId = methodNameId,
      methodSignatureStringId = javaScriptMethodSignatureStringId,
      sourceFileNameStringId = sourceFileNameStringId,
      classId = javaScriptClassId,
      lineNumber = line.toIntOrNull() ?: 0,
    )
  }

  override fun close() {
    hprofWriter.writeThreadEnd(threadId = javaScriptThreadId)
    hprofWriter.writeCpuSamples()
    hprofWriter.close()
  }

  private companion object {
    val STACK_FRAME_REGEX = Regex("    at ([^ ]+) \\(([^ :]+)(?::(\\d+))?\\)\n")
  }
}
