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

import app.cash.zipline.QuickJs
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Test

class ApplySourceMapToBytecodeTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Test fun happyPath() {
    // main.kt
    //  1  @JsExport
    //  2  fun sayHello() {
    //  3    goBoom3()
    //  4  }
    //  5  private fun goBoom3() {
    //  6    goBoom2()
    //  7  }
    //  8  private fun goBoom2() {
    //  9    goBoom1()
    // 10  }
    // 11  private fun goBoom1() {
    // 12    js(""${'"'}throw Error("boom!")""${'"'})
    // 13  }

    // kotlin-js-demo.js
    val js = """
       /*  1 */ (function (root, factory) {
       /*  2 */   if (typeof define === 'function' && define.amd)
       /*  3 */     define(['exports'], factory);
       /*  4 */   else if (typeof exports === 'object')
       /*  5 */     factory(module.exports);
       /*  6 */   else
       /*  7 */     root['demo'] = factory(typeof this['demo'] === 'undefined' ? {} : this['demo']);
       /*  8 */ }(this, function (_) {
       /*  9 */   'use strict';
       /* 10 */   function sayHello() {
       /* 11 */     goBoom3();
       /* 12 */   }
       /* 13 */   function goBoom3() {
       /* 14 */     goBoom2();
       /* 15 */   }
       /* 16 */   function goBoom2() {
       /* 17 */     goBoom1();
       /* 18 */   }
       /* 19 */   function goBoom1() {
       /* 20 */     throw Error('boom!');
       /* 21 */   }
       /* 22 */   _.sayHello = sayHello;
       /* 23 */   return _;
       /* 24 */ }));
       /* 25 */
       /* 26 */ //# sourceMappingURL=demo.js.map
       """.trimIndent()

    // kotlin-js-demo.js.map
    val sourceMap = """
      {
        "version": 3,
        "sources": [
          "throwException.kt",
          "<js-code>"
        ],
        "sourcesContent": [
          null,
          null
        ],
        "names": [],
        "mappings": ";;;;;;;;;;IAEE,S;;;IAGA,S;;;IAGA,S;;;IAGA,MCXI,KAAK,CAAC,OAAD,C;;;;;"
      }
      """.trimIndent()

    // Use QuickJS to compile a script into bytecode.
    val bytecode = quickJs.compile(js, "demo.js")
    val updatedBytecode = applySourceMapToBytecode(bytecode, sourceMap)
    quickJs.execute(updatedBytecode)
    val exception = assertFailsWith<Exception> {
      quickJs.evaluate("demo.sayHello()")
    }
    assertThat(exception.stackTraceToString()).startsWith("""
      |QuickJsException: boom!
      |	at JavaScript.goBoom1(throwException.kt)
      |	at JavaScript.goBoom2(throwException.kt:9)
      |	at JavaScript.goBoom3(throwException.kt:6)
      |	at JavaScript.sayHello(throwException.kt:3)
      |	at JavaScript.<eval>(?)
      |""".trimMargin())
  }

  @Test fun functionWithNoInstructions() {
    val js = """
       function doNothing() {
       }
       """.trimIndent()

    // kotlin-js-demo.js.map
    val sourceMap = """
      {
        "version": 3,
        "sources": [],
        "sourcesContent": [],
        "names": [],
        "mappings": ";;"
      }
      """.trimIndent()

    // Just confirm the empty function can be transformed successfully.
    val bytecode = quickJs.compile(js, "demo.js")
    applySourceMapToBytecode(bytecode, sourceMap)
  }
}
