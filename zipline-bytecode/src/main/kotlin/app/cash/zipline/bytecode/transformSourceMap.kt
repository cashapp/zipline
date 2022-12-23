/*
 * Copyright (C) 2022 Block, Inc.
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

/**
 * Kotlin/JS builds paths relative to the `rootDir/build/js/packages/moduleName/kotlin` directory,
 * which means source maps typically have several leading `../` segments that aren't useful when
 * diagnosing crashes.
 *
 * For example, here's a sample file path from a Kotlin/JS-generated source map:
 *
 * ```
 * ../../../../../go-boom/src/jsMain/kotlin/app/cash/zipline/testing/goBoom.kt
 * ```
 *
 * This strips off the leading `../` segments for more legible stacktraces, yielding a string like
 * this:
 *
 * ```
 * go-boom/src/jsMain/kotlin/app/cash/zipline/testing/goBoom.kt
 * ```
 */
fun SourceMap.removeLeadingDotDots(): SourceMap {
  return transformFilePaths { path ->
    path.replace(Regex("^(\\.\\./)+"), "")
  }
}

private fun SourceMap.transformFilePaths(
  filePathTransformer: (String) -> String,
): SourceMap {
  return SourceMap(
    version = version,
    file = file?.let(filePathTransformer),
    sourcesContent = sourcesContent,
    groups = groups.map { it.transformFilePaths(filePathTransformer) },
  )
}

private fun Group.transformFilePaths(
  filePathTransformer: (String) -> String,
): Group {
  return Group(segments.map { it.transformFilePaths(filePathTransformer) })
}

private fun Segment.transformFilePaths(
  filePathTransformer: (String) -> String,
): Segment {
  return Segment(
    startingColumn = startingColumn,
    source = source?.let(filePathTransformer),
    sourceLine = sourceLine,
    sourceColumn = sourceColumn,
    name = name,
  )
}
