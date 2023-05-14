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

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okio.Buffer
import org.junit.Test

class SourceMapParsingTest {
  @Test fun simpleSourceMap() {
    // main.kt
    //
    // 1   fun main() {
    // 2     val hello = "Hello world!"
    // 3     console.log(hello)
    // 4   }

    // kotlin-js-demo.js
    //
    // 1   (function (root, factory) {
    // 2     if (typeof define === 'function' && define.amd)
    // 3       define(['exports', 'kotlin'], factory);
    // 4     else if (typeof exports === 'object')
    // 5       factory(module.exports, require('kotlin'));
    // 6     else {
    // 7       if (typeof kotlin === 'undefined') {
    // 8         throw new Error("Error loading module 'kotlin-js-demo'. Its dependency 'kotlin' was not found. Please, check whether 'kotlin' is loaded prior to 'kotlin-js-demo'.");
    // 9       }root['kotlin-js-demo'] = factory(typeof this['kotlin-js-demo'] === 'undefined' ? {} : this['kotlin-js-demo'], kotlin);
    // 10    }
    // 11  }(this, function (_, Kotlin) {
    // 12    'use strict';
    // 13    function main() {
    // 14      var hello = 'Hello world!';
    // 15      console.log(hello);
    // 16    }
    // 17    _.main = main;
    // 18    main();
    // 19    Kotlin.defineModule('kotlin-js-demo', _);
    // 20    return _;
    // 21  }));

    // kotlin-js-demo.js.map
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

    val sourceMap = SourceMap.parse(sourceMapJson)
    assertEquals(3, sourceMap.version)
    assertEquals("kotlin-js-demo.js", sourceMap.file)

    // Matches line 2 in the source file.
    assertEquals(
      Segment(
        startingColumn = 5,
        source = "../../../../../src/main/kotlin/main.kt",
        sourceLine = 2,
        sourceColumn = 3,
        name = null,
      ),
      sourceMap.find(lineNumber = 14),
    )
    // Doesn't match any lines in the source file.
    assertNull(sourceMap.find(lineNumber = 17))
  }

  @Test fun readVarints() {
    val buffer = Buffer().writeUtf8("IAkpZI")
    assertEquals(4L, buffer.readVarint())
    assertEquals(0L, buffer.readVarint())
    assertEquals(12946L, buffer.readVarint())
    assertEquals(4L, buffer.readVarint())
    assertTrue(buffer.exhausted())
  }
}
