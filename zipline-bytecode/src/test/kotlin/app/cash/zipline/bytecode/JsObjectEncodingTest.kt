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
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import okio.Buffer
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Test

class JsObjectEncodingTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Test fun decodeAndEncode() {
    val evalFunction = assertRoundTrip(
      """
      |function greet(name) {
      |  return "hello, " + name;
      |}
      """.trimMargin(),
        "hello.js",
    )

    assertThat(evalFunction.name).isEqualTo("<eval>")
    assertThat(evalFunction.debug?.fileName).isEqualTo("hello.js")
    assertThat(evalFunction.debug?.lineNumber).isEqualTo(1)

    val greetFunction = evalFunction.constantPool.single() as JsFunctionBytecode
    assertThat(greetFunction.name).isEqualTo("greet")
    assertThat(greetFunction.argCount).isEqualTo(1)
    assertThat(greetFunction.locals.single().name).isEqualTo("name")
    assertThat(greetFunction.debug?.fileName).isEqualTo("hello.js")
    assertThat(greetFunction.debug?.lineNumber).isEqualTo(1)
  }

  @Test fun primitiveValues() {
    assertRoundTrip(
      """
      |function primitiveValues() {
      |  return [
      |    null,
      |    undefined,
      |    false,
      |    true,
      |    2147483647,
      |    2.7182818284590452354,
      |    "hello"
      |  ];
      |}
      """.trimMargin(),
    )
  }

  @Test fun atomsInNames() {
    val evalFunction = assertRoundTrip(
      """
      |function toString() {
      |  return "JSON"
      |}
      """.trimMargin(),
    )
    assertThat(evalFunction.name).isEqualTo("<eval>")
    val toStringFunction = evalFunction.constantPool.single() as JsFunctionBytecode
    assertThat(toStringFunction.name).isEqualTo("toString")
  }

  @Test fun lineNumbers() {
    val evalFunction = assertRoundTrip(
      """
      |function functionWithLineNumbers() {
      |  console.log("line 2");
      |  // this line intentionally left blank.
      |  // this line intentionally left blank.
      |  console.log("line 5");
      |  console.log("line 6");
      |  // this line intentionally left blank.
      |  console.log("line 8");
      |  return 0;
      |}
      """.trimMargin(),
    )

    assertThat(evalFunction.name).isEqualTo("<eval>")
    assertThat(assertLineNumbersRoundTrip(evalFunction.debug!!))
      .containsExactly(0, 10)

    val function = evalFunction.constantPool.single() as JsFunctionBytecode
    assertThat(assertLineNumbersRoundTrip(function.debug!!))
      .containsExactly(2, 5, 6, 8, 9)
  }

  @Test fun wideChars() {
    assertRoundTrip(
      """
      |function utf16Values() {
      |  return [
      |    "…",
      |    "Café 🍩", // NFC (6 code points)
      |    "Café 🍩", // NFD (7 code points)
      |  ];
      |}
      """.trimMargin(),
    )
  }

  @Test fun nonUtf8Strings() {
    assertRoundTrip(
      """
      |function nonUtf8Strings() {
      |  return [
      |    "\u00fe",
      |    "\u00ff"
      |  ];
      |}
      """.trimMargin(),
    )
  }

  /**
   * We had an off-by-one reading the `has_debug` bit from the flags in
   * `JsObjectReader.readFunction()`.
   *
   * The bug stayed hidden for a long time because the bit we were reading instead
   * (`arguments_allowed`) had always been true, and the `has_debug` had also always been true. We
   * only discovered the problem when upgrading to Kotlin 1.8.20, which includes JavaScript that
   * causes `arguments_allowed` to be false.
   *
   * This regression test reproduces that exact situation. The code sample is simplified from
   * `kotlin-kotlin-stdlib-js-ir.js` as emitted by the Kotlin 1.8.20 compiler.
   */
  @Test fun debugSymbolsDoesntCrash() {
    val evalFunction = assertRoundTrip(
      """
      |function createExternalThis(ctor, superExternalCtor, parameters, box) {
      |  var newCtor = class extends ctor {}
      |}
      |function newThrowable(message, cause) {
      |}
      """.trimMargin(),
        "hello.js",
    )

    assertThat(evalFunction.name).isEqualTo("<eval>")

    val createExternalThis = evalFunction.constantPool[0] as JsFunctionBytecode
    assertThat(createExternalThis.name).isEqualTo("createExternalThis")

    val newThrowable = evalFunction.constantPool[1] as JsFunctionBytecode
    assertThat(newThrowable.name).isEqualTo("newThrowable")
  }

  /**
   * We had a bug where we were using the wrong value to detect if debug symbols were present.
   * The bug only triggered on functions that called `super()`.
   */
  @Test fun debugSymbolsDoesntCrashOnSuperCall() {
    val evalFunction = assertRoundTrip(
      """
      |class Greeter {
      |  greet() {
      |    super.hello();
      |  }
      |}
      """.trimMargin(),
        "hello.js",
    )

    assertThat(evalFunction.name).isEqualTo("<eval>")
  }

  /** Returns the model object for the bytecode of [script]. */
  private fun assertRoundTrip(
    script: String,
    fileName: String = "test.js",
  ): JsFunctionBytecode {
    // Use QuickJS to compile a script into bytecode.
    val bytecode: ByteArray = quickJs.compile(script, fileName)

    // Confirm we can decode the bytecode.
    val reader = JsObjectReader(bytecode)
    val decoded = reader.use {
      reader.readJsObject()
    }

    // Confirm that encoding the model yields the original bytecode.
    val buffer = Buffer()
    JsObjectWriter(reader.atoms, buffer).use { writer ->
      writer.writeJsObject(decoded)
    }
    assertThat(buffer.readByteString()).isEqualTo(bytecode.toByteString())

    // Return the decoded model.
    return decoded as JsFunctionBytecode
  }

  /** Returns the line numbers only. */
  private fun assertLineNumbersRoundTrip(debug: Debug): List<Int> {
    val reader = LineNumberReader(debug.lineNumber, Buffer().write(debug.pc2Line))
    val result = mutableListOf<Int>()

    val buffer = Buffer()
    val writer = LineNumberWriter(debug.lineNumber, buffer)
    while (reader.next()) {
      writer.next(reader.pc, reader.line)
      result += reader.line
    }

    assertThat(buffer.readByteString()).isEqualTo(debug.pc2Line)
    return result
  }
}
