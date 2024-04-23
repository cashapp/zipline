/*
 * Copyright (C) 2024 Cash App
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

/**
 * Converts the source paths in Kotlin/JS source maps to strings appropriate for stack traces in
 * developer-facing output.
 *
 * This uses basic heuristics to remove unwanted information like the directory a particular repo
 * was in when it was compiled.
 */
internal class SourcePathCleaner {
  private val sourceDirectories = listOf(
    "/sources/",
    "/kotlin/",
    "/src/",
    "/source/",
  )

  private val packagePrefixes = listOf(
    "/com/",
  )

  fun clean(path: String): String {
    // Special case: the stdlib starts with src/kotlin/, but we only remove the src/ bit.
    if (path.startsWith("src/kotlin/")) {
      return path.removePrefix("src/")
    }

    // Find /src/ or similar, and take everything after that.
    for (sourceDirectory in sourceDirectories) {
      val sourcesIndex = path.lastIndexOf(sourceDirectory)
      if (sourcesIndex != -1) {
        return path.substring(sourcesIndex + sourceDirectory.length)
      }
    }

    // Find /com/ or similar and take the string that starts with com/.
    for (packagePrefix in packagePrefixes) {
      val packagePrefixIndex = path.indexOf(packagePrefix)
      if (packagePrefixIndex != -1) {
        return path.substring(packagePrefixIndex + 1)
      }
    }

    return path
  }
}
