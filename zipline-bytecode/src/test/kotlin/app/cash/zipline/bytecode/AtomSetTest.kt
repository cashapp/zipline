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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import kotlin.test.assertFailsWith
import okio.ByteString.Companion.decodeHex
import org.junit.Test

class AtomSetTest {
  private val helloJsString = "hello".toJsString()
  private val worldJsString = "world".toJsString()
  private val nullJsString = "null".toJsString()
  private val trueJsString = "true".toJsString()
  private val noSuchJsString = "no-such-string".toJsString()

  @Test fun happyPath() {
    val atomSet = MutableAtomSet(listOf(helloJsString, worldJsString))

    // Null and true are built-in atoms.
    assertThat(atomSet.idOf(nullJsString)).isGreaterThanOrEqualTo(0)
    assertThat(atomSet.idOf(trueJsString)).isGreaterThanOrEqualTo(0)

    // 'hello' and 'world' are not built-in atoms.
    assertThat(atomSet.idOf(helloJsString)).isGreaterThanOrEqualTo(0)
    assertThat(atomSet.idOf(worldJsString)).isGreaterThanOrEqualTo(0)

    assertFailsWith<IllegalArgumentException> {
      atomSet.idOf(noSuchJsString)
    }
  }

  @Test fun addNewAtom() {
    val atomSet = MutableAtomSet(listOf(helloJsString))
    assertThat(atomSet.add(worldJsString)).isTrue()
    assertThat(atomSet.idOf(worldJsString)).isGreaterThanOrEqualTo(0)
  }

  @Test fun addExistingAtom() {
    val atomSet = MutableAtomSet(listOf(helloJsString))
    assertThat(atomSet.add(trueJsString)).isFalse()
    assertThat(atomSet.idOf(trueJsString)).isGreaterThanOrEqualTo(0)
  }

  /**
   * Confirm that [AtomSet] keeps distinct indexes for values that have the same UTF-8 bytes but are
   * otherwise distinct. When encoding regex bytecode, QuickJS uses 8-bit strings that are not valid
   * UTF-8.
   */
  @Test fun atomSetHoldsNonUtf8Strings() {
    val unicodeReplacementChar = "\ufffd"
    val atomSet = MutableAtomSet(listOf())

    val fe = JsString(isWideChar = false, "fe".decodeHex())
    assertThat(fe.string).isEqualTo(unicodeReplacementChar)
    assertThat(atomSet.add(fe)).isTrue()

    val ff = JsString(isWideChar = false, "ff".decodeHex())
    assertThat(ff.string).isEqualTo(unicodeReplacementChar)
    assertThat(atomSet.add(ff)).isTrue()

    assertThat(atomSet.idOf(fe)).isNotEqualTo(atomSet.idOf(ff))
  }
}
