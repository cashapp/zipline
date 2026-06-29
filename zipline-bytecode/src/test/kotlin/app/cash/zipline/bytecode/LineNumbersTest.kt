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
import okio.ByteString.Companion.decodeHex
import org.junit.Test

class LineNumbersTest {
  @Test
  fun readGoldenValue() {
    // v5 pc2line per-PC entries: { op | (op=0, leb128(pc), slab128(line)) | slab128(column) }
    // (function line/column header is written by the bytecode reader, not here)
    val reader = LineNumberReader(1, 1, Buffer().write("1f0000081200".decodeHex()))
    assertThat(reader.next()).isTrue()
    assertThat(reader.pc).isEqualTo(6)
    assertThat(reader.line).isEqualTo(0)
    assertThat(reader.column).isEqualTo(1)
    assertThat(reader.next()).isTrue()
    assertThat(reader.pc).isEqualTo(14)
    assertThat(reader.line).isEqualTo(9)
    assertThat(reader.column).isEqualTo(1)
    assertThat(reader.next()).isFalse()
  }

  @Test
  fun writeGoldenValue() {
    val buffer = Buffer()
    LineNumberWriter(1, 1, buffer).use { writer ->
      writer.next(pc = 6, line = 0, column = 1)
      writer.next(pc = 14, line = 9, column = 1)
    }
    assertThat(buffer.readByteString()).isEqualTo("1f0000081200".decodeHex())
  }
}
