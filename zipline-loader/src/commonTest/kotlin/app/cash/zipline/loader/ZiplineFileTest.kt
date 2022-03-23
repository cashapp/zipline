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

package app.cash.zipline.loader

import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.IOException

class ZiplineFileTest {
  private val bytecode = "sample bytecode".encodeUtf8()

  @Test
  fun encodeAndDecode() {
    val ziplineFile = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode)
    val buffer = Buffer()
    ziplineFile.writeTo(buffer)
    val decodedZiplineFile = ZiplineFile.read(buffer)
    assertEquals(CURRENT_ZIPLINE_VERSION, decodedZiplineFile.ziplineVersion)
    assertEquals(bytecode, decodedZiplineFile.quickjsBytecode)
  }

  @Test
  fun decodeGoldenFile() {
    val goldenFile =
      "5a49504c494e45000134654c000000010000000f73616d706c652062797465636f6465".decodeHex()
    val buffer = Buffer().write(goldenFile)
    val decodedZiplineFile = ZiplineFile.read(buffer)
    assertEquals(CURRENT_ZIPLINE_VERSION, decodedZiplineFile.ziplineVersion)
    assertEquals(bytecode, decodedZiplineFile.quickjsBytecode)
  }

  @Test
  fun decodeWithUnknownSection() {
    // Append an extra section to our golden file and confirm that it's ignored. We want this so
    // if later we want to add new sections, we know old readers will silently ignore data they
    // don't understand. (We'll bump the zipline version when we need to break old clients.)
    val goldenFile =
      "5a49504c494e45000134654c000000010000000f73616d706c652062797465636f6465".decodeHex()
    val buffer = Buffer().write(goldenFile)
    buffer.writeInt(9999) // Section 9999 is unlikely
    buffer.writeInt(5) // Section 9999 length
    buffer.writeUtf8("hello")
    val decodedZiplineFile = ZiplineFile.read(buffer)
    assertEquals(CURRENT_ZIPLINE_VERSION, decodedZiplineFile.ziplineVersion)
    assertEquals(bytecode, decodedZiplineFile.quickjsBytecode)
  }

  @Test
  fun decodeUnknownVersionCrashes() {
    // We manually changed the version to an unknown one.
    val goldenFile =
      "5a49504c494e45000134654e000000010000000f73616d706c652062797465636f6465".decodeHex()
    val buffer = Buffer().write(goldenFile)
    val e = assertFailsWith<IOException> {
      ZiplineFile.read(buffer)
    }
    assertEquals("unsupported version [version=20211022][currentVersion=20211020]", e.message)
  }

  @Test
  fun decodeSectionTruncatedCrashes() {
    // We manually changed the version to an unknown one.
    val goldenFile =
      "5a49504c494e45000134654c000000010000000f73616d706c652062797465636f64".decodeHex()
    val buffer = Buffer().write(goldenFile)
    assertFailsWith<IOException> {
      ZiplineFile.read(buffer)
    }
  }

  @Test
  fun decodeNonZiplineFileCrashes() {
    val buffer = Buffer().writeUtf8("function hello() { };")
    val e = assertFailsWith<IOException> {
      ZiplineFile.read(buffer)
    }
    assertEquals("not a zipline file", e.message)
  }

  @Test
  fun decodeBytecodeSectionMissing() {
    val goldenFile = "5a49504c494e45000134654c".decodeHex()
    val buffer = Buffer().write(goldenFile)
    val e = assertFailsWith<IOException> {
      ZiplineFile.read(buffer)
    }
    assertEquals("QuickJS bytecode section missing", e.message)
  }

  @Test
  fun writeToByteString() {
    val ziplineFile = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode)
    val buffer = Buffer()
    ziplineFile.writeTo(buffer)
    val byteString = buffer.readByteString()
    assertEquals(byteString, ziplineFile.toByteString())
  }

  @Test
  fun readFromByteString() {
    val original = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode)
    val buffer = Buffer()
    original.writeTo(buffer)
    val ziplineFileBytes = buffer.readByteString()

    val expected = ZiplineFile.read(Buffer().write(ziplineFileBytes))
    val parsed = ziplineFileBytes.toZiplineFile()
    assertEquals(expected, parsed)
  }
}
