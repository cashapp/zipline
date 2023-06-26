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
package app.cash.zipline.api.validator.toml

import okio.BufferedSink

fun BufferedSink.writeTomlZiplineApi(api: TomlZiplineApi) {
  TomlZiplineApiWriter(this).writeApi(api)
}

/**
 * Super-limited writer for the tiny subset of TOML we use for Zipline API files.
 *
 * This doesn't support strings that require character escapes.
 */
internal class TomlZiplineApiWriter(
  private val sink: BufferedSink,
) {
  fun writeApi(api: TomlZiplineApi) {
    var first = true
    for (service in api.services) {
      if (!first) sink.writeUtf8("\n")
      first = false

      writeService(service)
    }
  }

  private fun writeService(service: TomlZiplineService) {
    sink.writeUtf8("[").writeUtf8(service.name).writeUtf8("]\n")
    sink.writeUtf8("\n")
    sink.writeUtf8("functions = [\n")
    var first = true
    for (function in service.functions) {
      if (!first) sink.writeUtf8("\n")
      first = false

      writeFunction(function)
    }
    sink.writeUtf8("]\n")
  }

  private fun writeFunction(function: TomlZiplineFunction) {
    val comment = function.leadingComment
    if (comment.isNotEmpty()) {
      sink.writeUtf8("  # ").writeUtf8(comment.replace("\n", "\n  # ")).writeUtf8("\n")
    }

    sink.writeUtf8("  \"").writeUtf8(function.id).writeUtf8("\",\n")
  }
}
