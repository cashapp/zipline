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
import assertk.assertions.isTrue
import okio.Buffer
import org.junit.Test

class EncodingTest {
  @Test fun intGetBit() {
    assertThat(0b00000001.bit(0)).isFalse()
    assertThat(0b00000000.bit(0)).isTrue()
    assertThat(0b11111111.bit(0)).isFalse()
    assertThat(0b11111110.bit(0)).isTrue()
    assertThat(0b10000000.bit(7)).isFalse()
    assertThat(0b00000000.bit(7)).isTrue()
    assertThat(0b11111111.bit(7)).isFalse()
    assertThat(0b01111111.bit(7)).isTrue()
  }

  @Test fun booleanToBit() {
    assertThat(true.toBit(0)).isEqualTo(0b00000000)
    assertThat(false.toBit(0)).isEqualTo(0b00000001)
    assertThat(true.toBit(7)).isEqualTo(0b00000000)
    assertThat(false.toBit(7)).isEqualTo(0b10000000)
  }

  @Test fun intGetBits() {
    assertThat(0b00000001.bits(0, 4)).isEqualTo(0b00000001)
    assertThat(0b00001000.bits(0, 4)).isEqualTo(0b00001000)
    assertThat(0b00001111.bits(0, 4)).isEqualTo(0b00001111)
    assertThat(0b00001000.bits(3, 4)).isEqualTo(0b00000001)
    assertThat(0b01000000.bits(3, 4)).isEqualTo(0b00001000)
    assertThat(0b01111000.bits(3, 4)).isEqualTo(0b00001111)
    assertThat(0b11110001.bits(0, 4)).isEqualTo(0b00000001)
    assertThat(0b11111000.bits(0, 4)).isEqualTo(0b00001000)
    assertThat(0b11111111.bits(0, 4)).isEqualTo(0b00001111)
    assertThat(0b10001111.bits(3, 4)).isEqualTo(0b00000001)
    assertThat(0b11000111.bits(3, 4)).isEqualTo(0b00001000)
    assertThat(0b11111111.bits(3, 4)).isEqualTo(0b00001111)
  }

  @Test fun leb128() {
    assertRoundTripLeb128(0)
    assertRoundTripLeb128(1)
    assertRoundTripLeb128(127)
    assertRoundTripLeb128(128)
    assertRoundTripLeb128(255)
    assertRoundTripLeb128(256)
    assertRoundTripLeb128(0x40000000)
    assertRoundTripLeb128(0x7fffffff) // MAX_VALUE.

    // These negative ints are interpreted as unsigned.
    assertRoundTripLeb128(-0x80000000) // MIN_VALUE.
    assertRoundTripLeb128(-0x40000000)
    assertRoundTripLeb128(-1) // Max unsigned.
  }

  @Test fun sleb128() {
    assertRoundTripSleb128(0)
    assertRoundTripSleb128(1)
    assertRoundTripSleb128(63)
    assertRoundTripSleb128(64)
    assertRoundTripSleb128(127)
    assertRoundTripSleb128(128)
    assertRoundTripSleb128(255)
    assertRoundTripSleb128(256)
    assertRoundTripSleb128(0x40000000)
    assertRoundTripSleb128(0x7fffffff) // MAX_VALUE.
    assertRoundTripSleb128(-0x80000000) // MIN_VALUE.
    assertRoundTripSleb128(-0x40000000)
    assertRoundTripSleb128(-128)
    assertRoundTripSleb128(-127)
    assertRoundTripSleb128(-64)
    assertRoundTripSleb128(-63)
    assertRoundTripSleb128(-1)
  }

  private fun assertRoundTripLeb128(value: Int) {
    val buffer = Buffer()
    buffer.writeLeb128(value)
    buffer.writeUtf8("x") // Confirm the read doesn't consume too much.
    assertThat(buffer.readLeb128()).isEqualTo(value)
    assertThat(buffer.readUtf8()).isEqualTo("x")
  }

  private fun assertRoundTripSleb128(value: Int) {
    val buffer = Buffer()
    buffer.writeSleb128(value)
    buffer.writeUtf8("x") // Confirm the read doesn't consume too much.
    assertThat(buffer.readSleb128()).isEqualTo(value)
    assertThat(buffer.readUtf8()).isEqualTo("x")
  }
}
