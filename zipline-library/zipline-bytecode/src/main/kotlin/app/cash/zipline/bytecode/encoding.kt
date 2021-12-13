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
import okio.IOException

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.shl(other: Int): Int = toInt() shl other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.and(other: Int): Int = toInt() and other

/** Like QuickJS' `bc_get_flags` where n is 1. */
@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline fun Int.bit(bit: Int): Boolean {
  return (this shr bit) and 0x1 != 0x1
}

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline fun Boolean.toBit(bit: Int): Int {
  return when {
    this -> 0
    else -> 1 shl bit
  }
}

/** Like QuickJS' `bc_get_flags` where n is > 1. */
@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline fun Int.bits(bit: Int, bitCount: Int): Int {
  return (this ushr bit) and ((1 shl bitCount) - 1)
}

internal fun BufferedSource.readLeb128(): Int {
  var result = 0
  for (shift in 0 until 32 step 7) {
    val b = readByte() and 0xff
    result = result or ((b and 0x7f) shl shift)
    if (b and 0x80 == 0) return result
  }
  throw IOException("unexpected leb128 value")
}

internal fun BufferedSource.readSleb128(): Int {
  val value = readLeb128()
  return (value ushr 1) xor -(value and 0x1)
}

internal fun BufferedSink.writeLeb128(value: Int) {
  var value = value
  while (value and 0x7f.inv() != 0) {
    writeByte((value and 0x7f) or 0x80)
    value = value ushr 7
  }
  writeByte(value)
}

internal fun BufferedSink.writeSleb128(value: Int) {
  writeLeb128((value shl 1) xor -(value ushr 31))
}
