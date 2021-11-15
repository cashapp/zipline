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
package app.cash.zipline

import app.cash.zipline.internal.SourceMap
import app.cash.zipline.internal.readVarint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Buffer

class SourceMapParsingTest {
  @Test fun simpleSourceMap() {
    val sourceMapJson = """
      {
        "version": 3,
        "file": "kotlin-js-demo.js",
        "sources": [
          "../../../../../src/main/kotlin/main.kt"
        ],
        "sourcesContent": [
          null
        ],
        "names": [],
        "mappings": ";;;;;;;;;;;;EAAA,gB;IACE,YAAY,c;IACZ,OAAQ,KAAI,KAAJ,C;EACV,C;;;;;;"
      }
      """.trimIndent()
    val element = Element(
      line = "<anonymous>",
      fileName = "demo.js",
      lineNumber = 14,
    )

    assertEquals(
      """
      at <anonymous> ../../../../../src/main/kotlin/main.kt:2
      """.trimIndent(),
      decodeStackTrace(
        stackTrace = listOf(element),
        sourceMapJsonLoader = { sourceMapJson },
      )
    )
  }

  @Test fun readVarints() {
    val buffer = Buffer().writeUtf8("IAkpZI")
    assertEquals(4L, buffer.readVarint())
    assertEquals(0L, buffer.readVarint())
    assertEquals(12946L, buffer.readVarint())
    assertEquals(4L, buffer.readVarint())
    assertTrue(buffer.exhausted())
  }

  private fun decodeStackTrace(
    stackTrace: List<Element>,
    sourceMapJsonLoader: (Element) -> String,
  ): String {
    return buildString {
      for (element in stackTrace) {
        val sourceMap = SourceMap.parse(sourceMapJsonLoader.invoke(element))
        val segment = sourceMap.find(element.lineNumber)
        appendLine(
          "at ${element.line} ${segment?.source ?: element.fileName}:${segment?.sourceLine}"
        )
      }
    }.trim()
  }

  class Element(
    val line: String,
    val fileName: String,
    val lineNumber: Int
  )
}
