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

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.IOException

data class ZiplineFile(
  val ziplineVersion: Int,
  val quickjsBytecode: ByteString,
) {
  fun writeTo(sink: BufferedSink) {
    sink.write(MAGIC_PREFIX)
    sink.writeInt(ziplineVersion)
    sink.writeInt(SECTION_HEADER_QUICKJS_BYTECODE)
    sink.writeInt(quickjsBytecode.size)
    sink.write(quickjsBytecode)
  }

  fun toByteString(): ByteString {
    val buffer = Buffer()
    writeTo(buffer)
    return buffer.readByteString()
  }

  companion object {
    /**
     * Reads from a bufferedSource to return a ZiplineFile.
     * This throws an IOException if the content is not a supported ZiplineFile.
     */
    fun read(source: BufferedSource): ZiplineFile {
      var quickjsBytecode: ByteString? = null
      if (source.readByteString(8) != MAGIC_PREFIX) {
        throw IOException("not a zipline file")
      }
      val ziplineVersion = source.readInt()
      if (ziplineVersion != CURRENT_ZIPLINE_VERSION) {
        throw IOException(
          "unsupported version [version=$ziplineVersion][currentVersion=$CURRENT_ZIPLINE_VERSION]",
        )
      }
      while (!source.exhausted()) {
        val sectionHeader = source.readInt()
        val sectionLength = source.readInt()
        quickjsBytecode = source.readSection(quickjsBytecode, sectionHeader, sectionLength)
      }

      return ZiplineFile(
        ziplineVersion = ziplineVersion,
        quickjsBytecode = quickjsBytecode ?: throw IOException("QuickJS bytecode section missing"),
      )
    }

    private fun BufferedSource.readSection(
      quickjsBytecode: ByteString? = null,
      sectionHeader: Int,
      sectionLength: Int,
    ): ByteString? = when (sectionHeader) {
      SECTION_HEADER_QUICKJS_BYTECODE -> {
        if (quickjsBytecode != null) {
          throw IOException("multiple QuickJS bytecode sections")
        }
        readByteString(sectionLength.toLong())
      }
      else -> {
        // Ignore unexpected section.
        skip(sectionLength.toLong())
        quickjsBytecode
      }
    }

    fun ByteString.toZiplineFile() = read(Buffer().write(this))
  }
}

private val MAGIC_PREFIX = "ZIPLINE\u0000".encodeUtf8()
val CURRENT_ZIPLINE_VERSION = 20211020
private val SECTION_HEADER_QUICKJS_BYTECODE = 1
