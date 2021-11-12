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

import app.cash.zipline.internal.SourceMapParser
import app.cash.zipline.internal.readVarint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath

class SourceMapParserTest {
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

  @Test fun mapStacktrace() {
    val stackTrace = listOf(
      Element("captureStack", "./kotlin-kotlin-stdlib-js-ir.js", 20884),
      Element("IllegalStateException_init_Create_0", "./kotlin-kotlin-stdlib-js-ir.js", 23220),
      Element("goBoom1", "./zipline-root-testing.js", 821),
      Element("goBoom2", "./zipline-root-testing.js", 818),
      Element("goBoom3", "./zipline-root-testing.js", 815),
      Element("<anonymous>", "./zipline-root-testing.js", 826),
      Element("<anonymous>", "./zipline-root-testing.js", 1088),
      Element("<anonymous>", "./zipline-root-zipline.js", 1130),
      Element("<anonymous>", "./zipline-root-zipline.js", 1145),
    )

    val root = "build/generated/testingJs".toPath()
    val fileSystem: FileSystem = FileSystem.SYSTEM
    assertEquals(
      """
      at captureStack ../../../../../coreRuntime.kt:86
      at IllegalStateException_init_Create_0 ./kotlin-kotlin-stdlib-js-ir.js:null
      at goBoom1 ../../../../../zipline/testing/src/jsMain/kotlin/app/cash/zipline/testing/echoJs.kt:54
      at goBoom2 ../../../../../zipline/testing/src/jsMain/kotlin/app/cash/zipline/testing/echoJs.kt:51
      at goBoom3 ../../../../../zipline/testing/src/jsMain/kotlin/app/cash/zipline/testing/echoJs.kt:48
      at <anonymous> ../../../../../zipline/testing/src/jsMain/kotlin/app/cash/zipline/testing/echoJs.kt:45
      at <anonymous> ../../../../../zipline/testing/src/jsMain/kotlin/app/cash/zipline/testing/echoJs.kt:60
      at <anonymous> ../../../../../zipline/src/commonMain/kotlin/app/cash/zipline/internal/bridge/Endpoint.kt:83
      at <anonymous> ./zipline-root-zipline.js:null
      """.trimIndent(),
      decodeStackTrace(
        stackTrace,
        sourceMapJsonLoader = { element ->
          fileSystem.read(root / "${element.fileName}.map") { readUtf8() }
        }
      )
    )
  }

  private fun decodeStackTrace(
    stackTrace: List<Element>,
    sourceMapJsonLoader: (Element) -> String,
  ): String {
    return buildString {
      for (element in stackTrace) {
        val sourceMap = SourceMapParser().parse(sourceMapJsonLoader.invoke(element))
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
