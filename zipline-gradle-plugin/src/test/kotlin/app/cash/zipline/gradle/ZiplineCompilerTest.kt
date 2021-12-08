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

package app.cash.zipline.gradle

import java.io.File
import org.junit.Test

class ZiplineCompilerTest {
  private val compiler = ZiplineCompiler()
  @Test
  fun `write to and read from zipline`() {
    assertZiplineCompile("src/test/resources/happyPath/")
  }

  @Test
  fun `no source map`() {
    assertZiplineCompile("src/test/resources/happyPathNoSourceMap/")
  }

  @Test
  fun `js with imports and exports`() {
    assertZiplineCompile("src/test/resources/jsWithImportsExports/")
  }

  private fun assertZiplineCompile(
    rootDir: String
  ) {
    val inputDir = File("$rootDir/jsBuild")
    val outputDir = File("$rootDir/build/zipline")
    outputDir.mkdirs()
    compiler.compile(inputDir, outputDir)
  }
}
