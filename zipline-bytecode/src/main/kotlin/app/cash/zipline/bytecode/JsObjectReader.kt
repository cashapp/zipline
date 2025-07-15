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
import okio.Closeable
import okio.IOException

class JsObjectReader(
  private val source: Buffer,
) : Closeable by source {
  private val sourceLength: Int = source.size.toInt()

  constructor(byteArray: ByteArray) : this(Buffer().write(byteArray))

  /**
   * Returns the current offset into the file in bytes.
   *
   * Define `DUMP_READ_OBJECT` in quickjs.c to get a full dump of a binary object. It includes a hex
   * offset that cross-references this offset.
   */
  private val offset: Int
    get() = sourceLength - source.size.toInt()

  lateinit var atoms: AtomSet
    private set

  fun readJsObject(): JsObject {
    check(!::atoms.isInitialized)
    atoms = readAtoms()
    return readObjectRecursive()
  }

  private fun readAtoms(): AtomSet {
    val version = source.readByte().toInt()
    if (version != BC_VERSION) {
      throw IOException("unexpected version (expected $BC_VERSION)")
    }
    val atomCount = source.readLeb128()
    val result = mutableListOf<JsString>()
    for (i in 0 until atomCount) {
      result += readJsString()
    }
    return MutableAtomSet(result)
  }

  private fun readJsString(): JsString {
    val stringLengthAndType = source.readLeb128()
    val isWideChar = stringLengthAndType and 0x1
    val stringLength = stringLengthAndType shr 1
    return when (isWideChar) {
      0x1 -> {
        val byteCount = stringLength.toLong() * 2
        JsString(isWideChar = true, bytes = source.readByteString(byteCount))
      }
      else -> {
        val byteCount = stringLength.toLong()
        JsString(isWideChar = false, bytes = source.readByteString(byteCount))
      }
    }
  }

  private fun readObjectRecursive(): JsObject {
    val objectOffset = this.offset
    return when (val tag = source.readByte().toInt()) {
      BC_TAG_NULL -> JsNull
      BC_TAG_UNDEFINED -> JsUndefined
      BC_TAG_BOOL_FALSE -> JsBoolean(false)
      BC_TAG_BOOL_TRUE -> JsBoolean(true)
      BC_TAG_INT32 -> JsInt(source.readSleb128())
      BC_TAG_FLOAT64 -> JsDouble(Double.fromBits(source.readLong()))
      BC_TAG_STRING -> readJsString()
      BC_TAG_FUNCTION_BYTECODE -> readFunction()
      else -> throw IOException("unsupported tag: $tag at 0x${objectOffset.toString(16)}")
    }
  }

  private fun readFunction(): JsFunctionBytecode {
    val flags = source.readShortLe().toInt()
    val jsMode = source.readByte()
    val functionName = readAtomString()
    val argCount = source.readLeb128()
    val varCount = source.readLeb128()
    val definedArgCount = source.readLeb128()
    val stackSize = source.readLeb128()
    val closureVarCount = source.readLeb128()
    val constantPoolCount = source.readLeb128()
    val bytecodeLength = source.readLeb128()
    val localCount = source.readLeb128()

    val locals = mutableListOf<JsVarDef>()
    for (i in 0 until localCount) {
      locals += readVarDef()
    }

    val closureVars = mutableListOf<JsClosureVar>()
    for (i in 0 until closureVarCount) {
      closureVars += readClosureVar()
    }

    val bytecode = source.readByteString(bytecodeLength.toLong())
    // TODO: fixup atoms within bytecode?

    val hasDebug = flags.bit(11)
    val debug: Debug? = if (hasDebug) readDebug() else null

    val constantPool = mutableListOf<JsObject>()
    for (i in 0 until constantPoolCount) {
      constantPool += readObjectRecursive()
    }

    return JsFunctionBytecode(
      flags = flags,
      jsMode = jsMode,
      name = functionName.string,
      argCount = argCount,
      varCount = varCount,
      definedArgCount = definedArgCount,
      stackSize = stackSize,
      locals = locals,
      closureVars = closureVars,
      bytecode = bytecode,
      constantPool = constantPool,
      debug = debug,
    )
  }

  private fun readAtomString(): JsString {
    val valueAndType = source.readLeb128()
    val value = valueAndType shr 1
    check(valueAndType and 0x1 != 0x1) { "expected a string but got an int" }
    return atoms.get(value)
  }

  private fun readAtomInt(): Int {
    val valueAndType = source.readLeb128()
    val value = valueAndType shr 1
    check(valueAndType and 0x1 == 0x1) { "expected an int but got a string" }
    return value
  }

  private fun readVarDef(): JsVarDef {
    val name = readAtomString()
    val scopeLevel = source.readLeb128()
    val scopeNext = source.readLeb128() - 1
    val flags = source.readByte().toInt()
    return JsVarDef(
      name = name.string,
      scopeLevel = scopeLevel,
      scopeNext = scopeNext,
      kind = flags.bits(bit = 0, bitCount = 4),
      isConst = flags.bit(4),
      isLexical = flags.bit(5),
      isCaptured = flags.bit(6),
    )
  }

  private fun readClosureVar(): JsClosureVar {
    val name = readAtomString()
    val varIndex = source.readLeb128()
    val flags = source.readByte().toInt()
    return JsClosureVar(
      name = name.string,
      varIndex = varIndex,
      isLocal = flags.bit(0),
      isArg = flags.bit(1),
      isConst = flags.bit(2),
      isLexical = flags.bit(3),
      kind = flags.bits(bit = 4, bitCount = 4),
    )
  }

  private fun readDebug(): Debug {
    val fileName = readAtomString()
    val lineNumber = source.readLeb128()
    val pc2lineLength = source.readLeb128()
    val pc2line = source.readByteString(pc2lineLength.toLong())
    return Debug(
      fileName = fileName.string,
      lineNumber = lineNumber,
      pc2Line = pc2line,
    )
  }
}
