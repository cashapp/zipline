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
import okio.ByteString
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
      BC_TAG_BIG_INT -> readBigInt()
      else -> throw IOException("unsupported tag: $tag at 0x${objectOffset.toString(16)}")
    }
  }

  /**
   * Reads a `BC_TAG_BIG_INT` payload as defined by `JS_ReadBigInt` in `native/quickjs/quickjs.c`:
   *
   *   ```
   *   BC_TAG_BIG_INT   (1 byte, value 10)
   *   leb128: len      // number of bytes in the two's complement representation; 0 means zero
   *   if len > 0:
   *     u32 * (len / 4)        // low limbs, little-endian
   *     u8  * (len % 4)        // top byte(s), sign-extended on read
   *   ```
   *
   * The top byte(s) are sign-extended so the final limb is treated as signed in QuickJS'
   * representation. We strip the sign extension here and stash the sign in [JsBigInt.exponent]
   * — the writer will re-add it.
   */
  private fun readBigInt(): JsBigInt {
    val byteLen = source.readLeb128()
    if (byteLen == 0) {
      return JsBigInt(limbs = listOf(0L))
    }
    val fullLimbs = byteLen / 4
    val tailBytes = byteLen % 4
    val limbs = ArrayList<Long>(fullLimbs + (if (tailBytes > 0) 1 else 0))
    for (i in 0 until fullLimbs) {
      limbs += source.readIntLe().toLong() and 0xFFFFFFFFL
    }
    if (tailBytes > 0) {
      var raw = 0L
      for (i in 0 until tailBytes) {
        raw = raw or (source.readByte().toLong() and 0xFFL shl (i * 8))
      }
      // Strip the sign extension the QuickJS writer applies to the top limb so the limbs
      // list round-trips losslessly through the writer.
      val topBits = tailBytes * 8
      val mask = (1L shl topBits) - 1L
      limbs += raw and mask
    }
    return JsBigInt(limbs = limbs)
  }

  private fun readFunction(): JsFunctionBytecode {
    val flags = source.readShortLe().toInt()
    val jsMode = source.readByte()
    val functionName = readAtomString()
    val argCount = source.readLeb128()
    val varCount = source.readLeb128()
    val definedArgCount = source.readLeb128()
    val stackSize = source.readLeb128()
    val varRefCount = source.readLeb128()
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

    val hasDebug = !flags.bit(10)
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
      varRefCount = varRefCount,
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
    val scopeNext = source.readLeb128() - 1
    val varRefIdx = source.readLeb128()
    val flags = source.readByte().toInt()
    return JsVarDef(
      name = name.string,
      scopeNext = scopeNext,
      varRefIdx = varRefIdx,
      kind = flags.bits(bit = 0, bitCount = 4),
      isConst = flags.bit(4),
      isLexical = flags.bit(5),
      isCaptured = flags.bit(6),
      hasScope = flags.bit(7),
    )
  }

  private fun readClosureVar(): JsClosureVar {
    val name = readAtomString()
    val varIndex = source.readLeb128()
    val flags = source.readShortLe().toInt()
    return JsClosureVar(
      name = name.string,
      varIndex = varIndex,
      closureType = flags.bits(bit = 0, bitCount = 3),
      isConst = flags.bit(3),
      isLexical = flags.bit(4),
      kind = flags.bits(bit = 5, bitCount = 4),
    )
  }

  private fun readDebug(): Debug {
    val fileName = readAtomString()
    val pc2lineLength = source.readLeb128()
    val pc2lineBytes = source.readByteString(pc2lineLength.toLong())
    val functionLine: Int
    val functionColumn: Int
    val pc2Line: ByteString
    if (pc2lineLength > 0) {
      val pc2lineSource = Buffer().write(pc2lineBytes)
      functionLine = pc2lineSource.readLeb128() + 1
      functionColumn = pc2lineSource.readLeb128() + 1
      pc2Line = pc2lineSource.readByteString()
    } else {
      functionLine = 0
      functionColumn = 0
      pc2Line = pc2lineBytes
    }
    val sourceLen = source.readLeb128()
    val source = if (sourceLen > 0) source.readByteString(sourceLen.toLong()) else null
    return Debug(
      fileName = fileName.string,
      line = functionLine,
      column = functionColumn,
      pc2Line = pc2Line,
      source = source,
    )
  }
}
