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

import okio.Buffer
import okio.BufferedSink
import okio.Closeable

/**
 * Encodes a [JsObject] as bytes.
 *
 * @param atoms the mapping from string to integer used to encode the object. If the object was
 *     decoded with a [JsObjectReader], it should be encoded with the same atoms.
 */
class JsObjectWriter(
  private val atoms: AtomSet,
  private val sink: BufferedSink,
) : Closeable by sink {
  private var used: Boolean = false

  fun writeJsObject(value: JsObject) {
    check(!used)
    used = true

    writeAtoms()
    writeObjectRecursive(value)
  }

  private fun writeAtoms() {
    sink.writeByte(BC_VERSION)
    sink.writeLeb128(atoms.strings.size)
    for (s in atoms.strings) {
      writeJsString(s)
    }
  }

  private fun writeJsString(value: JsString) {
    if (value.isWideChar) {
      val stringLength = value.bytes.size / 2
      sink.writeLeb128((stringLength shl 1) or 0x1)
      sink.write(value.bytes)
    } else {
      val stringLength = value.bytes.size
      sink.writeLeb128((stringLength shl 1) or 0x0)
      sink.write(value.bytes)
    }
  }

  private fun writeObjectRecursive(value: JsObject) {
    when (value) {
      is JsNull -> sink.writeByte(BC_TAG_NULL)

      is JsUndefined -> sink.writeByte(BC_TAG_UNDEFINED)

      is JsBoolean -> {
        val tag = when {
          value.value -> BC_TAG_BOOL_TRUE
          else -> BC_TAG_BOOL_FALSE
        }
        sink.writeByte(tag)
      }

      is JsInt -> {
        sink.writeByte(BC_TAG_INT32)
        sink.writeSleb128(value.value)
      }

      is JsDouble -> {
        sink.writeByte(BC_TAG_FLOAT64)
        sink.writeLong(value.value.toRawBits())
      }

      is JsString -> {
        sink.writeByte(BC_TAG_STRING)
        writeJsString(value)
      }

      is JsFunctionBytecode -> {
        sink.writeByte(BC_TAG_FUNCTION_BYTECODE)
        writeFunction(value)
      }

      is JsBigInt -> {
        sink.writeByte(BC_TAG_BIG_INT)
        writeBigInt(value)
      }
    }
  }

  private fun writeBigInt(value: JsBigInt) {
    // The BigInt limbs are unsigned 32-bit values; QuickJS encodes the byte length of
    // the two's-complement representation. For zero, write a single 0 byte of length 0.
    val limbs = value.limbs
    val isZero = limbs.isEmpty() || (limbs.size == 1 && limbs[0] == 0L)
    if (isZero) {
      sink.writeLeb128(0)
      return
    }
    // Determine byte length (excluding trailing sign-only bytes).
    var topLimb = limbs.last()
    var topByteCount = 4
    while (topByteCount > 1) {
      val shift = (topByteCount - 1) * 8
      val b = ((topLimb shr shift) and 0xFFL).toInt()
      if (b != 0x00 && b != 0xFF) break
      val b2 = ((topLimb shr (shift - 8)) and 0xFFL).toInt()
      if ((b and 1) != (b2 and 1)) break
      topByteCount--
    }
    val fullLimbs = limbs.size - 1
    val byteLen = fullLimbs * 4 + topByteCount
    sink.writeLeb128(byteLen)
    for (i in 0 until fullLimbs) {
      sink.writeIntLe(limbs[i].toInt())
    }
    for (i in 0 until topByteCount) {
      sink.writeByte(((topLimb shr (i * 8)) and 0xFFL).toInt())
    }
  }

  private fun writeFunction(value: JsFunctionBytecode) {
    val debug = value.debug
    val hasDebug = debug != null
    val flags = if (hasDebug) value.flags else value.flags and (1 shl 10).inv()
    sink.writeShortLe(flags)
    sink.writeByte(value.jsMode.toInt())
    writeAtom(value.name.toJsString())
    sink.writeLeb128(value.argCount)
    sink.writeLeb128(value.varCount)
    sink.writeLeb128(value.definedArgCount)
    sink.writeLeb128(value.stackSize)
    sink.writeLeb128(value.varRefCount)
    sink.writeLeb128(value.closureVars.size)
    sink.writeLeb128(value.constantPool.size)
    sink.writeLeb128(value.bytecode.size)
    sink.writeLeb128(value.locals.size)

    for (local in value.locals) {
      writeVarDef(local)
    }

    for (closureVar in value.closureVars) {
      writeClosureVar(closureVar)
    }

    // TODO: fixup atoms within bytecode?
    sink.write(value.bytecode)

    if (hasDebug) {
      writeDebug(debug!!)
    }

    for (constant in value.constantPool) {
      writeObjectRecursive(constant)
    }
  }

  private fun writeAtom(value: JsString) {
    val valueAndType = atoms.idOf(value) shl 1
    sink.writeLeb128(valueAndType)
  }

  private fun writeVarDef(value: JsVarDef) {
    writeAtom(value.name.toJsString())
    sink.writeLeb128(value.scopeNext + 1)
    sink.writeLeb128(value.varRefIdx)
    sink.writeByte(
      value.kind or
        value.isConst.toBit(4) or
        value.isLexical.toBit(5) or
        value.isCaptured.toBit(6) or
        value.hasScope.toBit(7),
    )
  }

  private fun writeClosureVar(value: JsClosureVar) {
    writeAtom(value.name.toJsString())
    sink.writeLeb128(value.varIndex)
    sink.writeShortLe(
      value.closureType or
        (value.isConst.toBit(3)) or
        (value.isLexical.toBit(4)) or
        (value.kind shl 5),
    )
  }

  private fun writeDebug(debug: Debug) {
    writeAtom(debug.fileName.toJsString())
    val pc2lineBuffer = Buffer()
    pc2lineBuffer.writeLeb128(debug.line - 1)
    pc2lineBuffer.writeLeb128(debug.column - 1)
    pc2lineBuffer.write(debug.pc2Line)
    val pc2lineBytes = pc2lineBuffer.readByteArray()
    sink.writeLeb128(pc2lineBytes.size)
    sink.write(pc2lineBytes)
    if (debug.source != null) {
      sink.writeLeb128(debug.source.size)
      sink.write(debug.source)
    } else {
      sink.writeLeb128(0)
    }
  }
}
