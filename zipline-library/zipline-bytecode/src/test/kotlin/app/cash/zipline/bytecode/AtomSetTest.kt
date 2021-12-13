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

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test

class AtomSetTest {
  @Test fun happyPath() {
    val atomSet = MutableAtomSet(listOf("hello", "world"))

    // Null and true are built-in atoms.
    assertThat(atomSet.idOf("null")).isAtLeast(0)
    assertThat(atomSet.idOf("true")).isAtLeast(0)

    // 'hello' and 'world' are not built-in atoms.
    assertThat(atomSet.idOf("hello")).isAtLeast(0)
    assertThat(atomSet.idOf("world")).isAtLeast(0)

    assertFailsWith<IllegalArgumentException> {
      atomSet.idOf("no-such-string")
    }
  }

  @Test fun addNewAtom() {
    val atomSet = MutableAtomSet(listOf("hello"))
    assertThat(atomSet.add("world")).isTrue()
    assertThat(atomSet.idOf("world")).isAtLeast(0)
  }

  @Test fun addExistingAtom() {
    val atomSet = MutableAtomSet(listOf("hello"))
    assertThat(atomSet.add("true")).isFalse()
    assertThat(atomSet.idOf("true")).isAtLeast(0)
  }
}
