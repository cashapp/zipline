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
package app.cash.zipline.bytecode

import okio.BufferedSink
import okio.BufferedSource
import okio.Closeable

/** Decode the pc2line table. */
class LineNumberReader(
  private val source: BufferedSource,
) : Closeable by source {
  var pc: Int = 0
  var line: Int = source.readLeb128() + 1
  var column: Int = source.readLeb128() + 1

  fun next(): Boolean {
    if (source.exhausted()) return false

    val op = source.readByte().toInt()
    val diffPc: Int
    val diffLine: Int
    if (op != 0) {
      val parts = op - PC2LINE_OP_FIRST
      diffPc = (parts / PC2LINE_RANGE)
      diffLine = (parts % PC2LINE_RANGE) + PC2LINE_BASE
    } else {
      diffPc = source.readLeb128()
      diffLine = source.readSleb128()
    }

    val diffColumn: Int = source.readSleb128()

    pc += diffPc
    line += diffLine
    column += diffColumn

    return true
  }
}

/** Encode a pc2line table. */
class LineNumberWriter(
  private val sink: BufferedSink,
  private var lastLine: Int = 0,
  private var lastColumn: Int = 0,
) : Closeable by sink {
  private var lastPc = 0

  init {
    sink.writeLeb128(lastLine - 1)
    sink.writeLeb128(lastColumn - 1)
  }

  fun next(pc: Int, line: Int, column: Int) {
    if (line < 0) return // Drop negative line numbers.

    val diffPc = pc - lastPc
    val diffLine = line - lastLine
    val diffColumn = column - lastColumn

    if (diffLine == 0 && diffColumn == 0) return // Nothing to do.
    if (diffPc < 0) return // PC may only advance.

    val linePart = diffLine - PC2LINE_BASE
    if (linePart in 0 until PC2LINE_RANGE && diffPc <= PC2LINE_DIFF_PC_MAX) {
      val pcPart = diffPc * PC2LINE_RANGE
      sink.writeByte(linePart + pcPart + PC2LINE_OP_FIRST)
    } else {
      sink.writeByte(0)
      sink.writeLeb128(diffPc)
      sink.writeSleb128(diffLine)
    }

    sink.writeSleb128(diffColumn)

    lastPc = pc
    lastLine = line
    lastColumn = column
  }
}

/* for the encoding of the pc2line table */
private const val PC2LINE_BASE = -1
private const val PC2LINE_RANGE = 5
private const val PC2LINE_OP_FIRST = 1
private const val PC2LINE_DIFF_PC_MAX = ((255 - PC2LINE_OP_FIRST) / PC2LINE_RANGE)
