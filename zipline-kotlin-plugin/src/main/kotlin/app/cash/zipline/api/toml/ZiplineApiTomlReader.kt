/*
 * Copyright (C) 2023 Cash App
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
package app.cash.zipline.api.toml

import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.IOException
import okio.Options

fun BufferedSource.readZiplineApi(): TomlZiplineApi {
  return TomlZiplineApiReader(this).readServices()
}

/**
 * Super-limited reader for the tiny subset of TOML we use for Zipline API files.
 *
 * Among other things, this doesn't support:
 *
 *  - keys or values not within a table
 *  - keys that aren't `functions`
 *  - values that aren't arrays of strings
 *  - escaped string contents
 *
 * But it does capture comments.
 */
internal class TomlZiplineApiReader(
  private val source: BufferedSource,
) {
  fun readServices(): TomlZiplineApi {
    val services = mutableListOf<TomlZiplineService>()

    while (true) {
      readComment()

      val token = source.select(readServicesToken)
      when {
        // [com.example.SampleService]
        token == readServicesTokenOpenBrace -> {
          val serviceName = readTableHeader()
          services += readService(serviceName)
        }
        source.exhausted() -> break
        else -> throw IOException("expected '['")
      }
    }

    return TomlZiplineApi(services)
  }

  private fun readService(serviceName: String): TomlZiplineService {
    val functions = mutableListOf<TomlZiplineFunction>()

    while (true) {
      readComment()

      when (source.select(readServiceToken)) {
        // functions = [ ... ]
        readServicesTokenFunctions -> {
          skipWhitespace()
          if (source.select(equals) == -1) throw IOException("expected '='")
          skipWhitespace()
          functions += readFunctions()
        }
        else -> break
      }
    }

    return TomlZiplineService(
      name = serviceName,
      functions = functions,
    )
  }

  private fun readFunctions(): List<TomlZiplineFunction> {
    val result = mutableListOf<TomlZiplineFunction>()

    readComment()
    if (source.select(openBrace) == -1) throw IOException("expected '['")

    while (true) {
      val comment = readComment()
      when (source.select(readFunctionToken)) {
        readFunctionQuote -> {
          val functionId = readString()
          skipWhitespace()
          result += TomlZiplineFunction(comment ?: "", functionId)
          when (source.select(afterFunctionToken)) {
            afterFunctionComma -> Unit
            afterFunctionCloseBrace -> break
            else -> throw IOException("expected ',' or ']'")
          }
        }
        readFunctionCloseBrace -> break
        else -> throw IOException("expected '\"' or ']'")
      }
    }

    return result
  }

  private fun readTableHeader(): String {
    val closeBrace = source.indexOf(']'.code.toByte())
    if (closeBrace == -1L) throw IOException("unterminated '['")
    val result = source.readUtf8(closeBrace)
    require(source.readByte() == ']'.code.toByte())
    return result
  }

  private fun readString(): String {
    val closeQuote = source.indexOf('"'.code.toByte())
    if (closeQuote == -1L) throw IOException("unterminated '\"'")
    val result = source.readUtf8(closeQuote)
    require(source.readByte() == '"'.code.toByte())
    return result
  }

  /** Read a potentially multi-line comment as a single string. */
  private fun readComment(): String? {
    var result: StringBuilder? = null

    while (true) {
      skipWhitespace()
      if (source.select(comment) == -1) break

      if (result == null) {
        result = StringBuilder()
      } else {
        result.append("\n")
      }

      val line = source.readUtf8Line() ?: ""
      result.append(line.trimEnd())
    }

    return result?.toString()
  }

  private fun skipWhitespace() {
    while (source.select(whitespace) != -1) {
      // Skip.
    }
  }

  private companion object {
    val readServicesToken = Options.of(
      "[".encodeUtf8(),
    )

    const val readServicesTokenOpenBrace = 0

    val readServiceToken = Options.of(
      "functions".encodeUtf8(),
    )

    const val readServicesTokenFunctions = 0

    val readFunctionToken = Options.of(
      "\"".encodeUtf8(),
      "]".encodeUtf8(),
    )

    const val readFunctionQuote = 0
    const val readFunctionCloseBrace = 1

    val afterFunctionToken = Options.of(
      ",".encodeUtf8(),
      "]".encodeUtf8(),
    )

    const val afterFunctionComma = 0
    const val afterFunctionCloseBrace = 1

    val whitespace = Options.of(
      " ".encodeUtf8(),
      "\t".encodeUtf8(),
      "\r".encodeUtf8(),
      "\n".encodeUtf8(),
    )

    val comment = Options.of(
      "# ".encodeUtf8(), // Prefer to skip a space after a comment.
      "#".encodeUtf8(),
    )

    val equals = Options.of(
      "=".encodeUtf8(),
    )

    val openBrace = Options.of(
      "[".encodeUtf8(),
    )
  }
}
