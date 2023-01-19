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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.BufferedSource
import okio.IOException

@Serializable
internal data class SourceMapJson(
  val version: Int,
  val file: String? = null,
  val sources: List<String>,
  val sourcesContent: List<String?>,
  val names: List<String>,
  val mappings: String,
  val sourceRoot: String? = null,
)

data class Group(
  val segments: List<Segment>,
)

data class Segment(
  val startingColumn: Long,
  val source: String?,
  val sourceLine: Long,
  val sourceColumn: Long,
  val name: String?,
)

data class SourceMap(
  val version: Int,
  val file: String?,
  val sourcesContent: List<String?>,
  val groups: List<Group>,
) {
  fun find(lineNumber: Int): Segment? {
    if (lineNumber < 1 || lineNumber > groups.size) return null
    return groups[lineNumber - 1].segments.firstOrNull()
  }

  companion object {
    /**
     * Parses the contents of a source map file to enable mapping from the elements in the
     * transformed (e.g. minified) source to the elements in the original source. This is an
     * implementation of the [Source Map Revision 3 Proposal](https://sourcemaps.info/spec.html).
     *
     * @param sourceMapJson contents of the source map file in JSON format.
     */
    fun parse(sourceMapJson: String): SourceMap {
      val sourceMap = Json.decodeFromString(SourceMapJson.serializer(), sourceMapJson)

      val buffer = Buffer()

      var sourceIndex = 0
      var sourceLine = 0L
      var sourceColumn = 0L
      var nameIndex = 0

      val groups = mutableListOf<Group>()
      for (group in sourceMap.mappings.split(";")) {
        var startingColumn = 0L

        val segments = mutableListOf<Segment>()
        for (segment in group.split(",")) {
          if (segment.isEmpty()) continue
          buffer.writeUtf8(segment)

          startingColumn += buffer.readVarint()

          // Read an optional updated source file, column, and row.
          if (!buffer.exhausted()) {
            sourceIndex += buffer.readVarint().toInt()
            sourceLine += buffer.readVarint()
            sourceColumn += buffer.readVarint()
          }

          // Read an optional name.
          if (!buffer.exhausted()) {
            nameIndex += buffer.readVarint().toInt()
          }

          if (!buffer.exhausted()) {
            throw IOException("unexpected part: $segment")
          }

          check(sourceIndex >= -1)
          check(nameIndex >= -1)
          check(sourceLine >= -1) {
            "unexpected source line: $sourceLine"
          }
          check(sourceColumn >= -1)

          val source = sourceMap.sources.elementAtOrNull(sourceIndex)
          val name = sourceMap.names.elementAtOrNull(nameIndex)
          segments += Segment(
            startingColumn = startingColumn + 1,
            source = source,
            sourceLine = sourceLine + 1,
            sourceColumn = sourceColumn + 1,
            name = name,
          )
        }

        groups += Group(segments)
      }

      return SourceMap(
        version = sourceMap.version,
        file = sourceMap.file,
        sourcesContent = sourceMap.sourcesContent,
        groups = groups,
      )
    }
  }
}

internal fun BufferedSource.readVarint(): Long {
  var shift = 0
  var result = 0L
  while (!exhausted()) {
    val b = readBase64Character()
    result += (b and 0x1F) shl shift
    if ((b and 0x20) == 0) {
      val unsigned = result ushr 1
      return when {
        (result and 0x1) == 0x1L -> -unsigned
        else -> unsigned
      }
    }
    shift += 5
  }
  throw IOException("malformed varint")
}

internal fun BufferedSource.readBase64Character(): Int {
  return when (val c = readByte().toInt().toChar()) {
    in 'A'..'Z' -> {
      // char ASCII value
      //  A    65    0
      //  Z    90    25 (ASCII - 65)
      c.code - 65
    }
    in 'a'..'z' -> {
      // char ASCII value
      //  a    97    26
      //  z    122   51 (ASCII - 71)
      c.code - 71
    }
    in '0'..'9' -> {
      // char ASCII value
      //  0    48    52
      //  9    57    61 (ASCII + 4)
      c.code + 4
    }
    '+', '-' -> 62
    '/', '_' -> 63
    else -> throw IOException("Unexpected character")
  }
}
