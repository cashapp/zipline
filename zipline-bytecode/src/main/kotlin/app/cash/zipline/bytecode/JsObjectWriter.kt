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
    }
  }

  private fun writeFunction(value: JsFunctionBytecode) {
    sink.writeShortLe(value.flags)
    sink.writeByte(value.jsMode.toInt())
    writeAtom(value.name.toJsString())
    sink.writeLeb128(value.argCount)
    sink.writeLeb128(value.varCount)
    sink.writeLeb128(value.definedArgCount)
    sink.writeLeb128(value.stackSize)
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

    if (value.debug != null) {
      writeDebug(value.debug)
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
    sink.writeLeb128(value.scopeLevel)
    sink.writeLeb128(value.scopeNext + 1)
    sink.writeByte(
      value.kind or
        value.isConst.toBit(4) or
        value.isLexical.toBit(5) or
        value.isCaptured.toBit(6),
    )
  }

  private fun writeClosureVar(value: JsClosureVar) {
    writeAtom(value.name.toJsString())
    sink.writeLeb128(value.varIndex)
    sink.writeByte(
      value.isLocal.toBit(0) or
        value.isArg.toBit(1) or
        value.isConst.toBit(2) or
        value.isLexical.toBit(3) or
        (value.kind shl 4),
    )
  }

  private fun writeDebug(debug: Debug) {
    writeAtom(debug.fileName.toJsString())
    sink.writeLeb128(debug.lineNumber)
    sink.writeLeb128(debug.pc2Line.size)
    sink.write(debug.pc2Line)
  }
}
