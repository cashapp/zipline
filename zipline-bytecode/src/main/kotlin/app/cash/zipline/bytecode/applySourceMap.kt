/*
 * Copyright (C) 2021 Square, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package app.cash.zipline.bytecode

import okio.Buffer

/**
 * Combines QuickJS bytecode with a JavaScript source map to produce new QuickJS bytecode that
 * bakes in the source map. Exceptions thrown will have the stack traces as if the original sources
 * were compiled.
 *
 * We use this to get Kotlin line numbers in our QuickJS bytecode.
 */
fun applySourceMapToBytecode(
  jsBytecode: ByteArray,
  sourceMap: SourceMap,
): ByteArray {
  val jsReader = JsObjectReader(jsBytecode)
  val jsObject = jsReader.use {
    jsReader.readJsObject()
  }

  val atoms = jsReader.atoms.toMutableAtomSet()
  val rewriter = SourceMapBytecodeRewriter(
    sourceMap = sourceMap,
    atoms = atoms,
  )

  val ktObject = with(rewriter) {
    jsObject.jsToKt()
  }

  val result = Buffer()
  JsObjectWriter(atoms, result).use { writer ->
    writer.writeJsObject(ktObject)
  }
  return result.readByteArray()
}

private class SourceMapBytecodeRewriter(
  val sourceMap: SourceMap,
  val atoms: MutableAtomSet,
) {
  fun JsObject.jsToKt(): JsObject {
    return when (this) {
      is JsFunctionBytecode -> {
        copy(
          debug = debug?.jsToKt(),
          constantPool = constantPool.map { it.jsToKt() },
        )
      }
      else -> this
    }
  }

  fun Debug.jsToKt(): Debug {
    val ktPc2LineBuffer = Buffer()

    val jsReader = LineNumberReader(
      functionLineNumber = lineNumber,
      source = Buffer().write(pc2Line),
    )

    var ktFileName: String? = null
    var functionKtLineNumber: Int = -1
    lateinit var ktWriter: LineNumberWriter
    while (jsReader.next()) {
      val segment = sourceMap.find(jsReader.line)
      val instructionKtLineNumber = segment?.sourceLine?.toInt() ?: jsReader.line

      // If we haven't initialized declaration-level data, do that now. We'd prefer to map from the
      // source declaration line number, but we can't because that information isn't in the source
      // map. (It maps instructions, not declarations).
      if (ktFileName == null) {
        ktFileName = segment?.source?.also {
          atoms.add(it)
        }
        functionKtLineNumber = instructionKtLineNumber
        ktWriter = LineNumberWriter(functionKtLineNumber, ktPc2LineBuffer)
      }

      ktWriter.next(jsReader.pc, instructionKtLineNumber)
    }

    return Debug(
      fileName = ktFileName ?: fileName,
      lineNumber = functionKtLineNumber,
      pc2Line = ktPc2LineBuffer.readByteString(),
    )
  }
}
