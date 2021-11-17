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

import app.cash.zipline.CURRENT_ZIPLINE_VERSION
import app.cash.zipline.QuickJs
import app.cash.zipline.ZiplineFile
import app.cash.zipline.bytecode.applySourceMapToBytecode
import java.io.File
import okio.ByteString.Companion.toByteString
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental

abstract class ZiplineCompileTask : DefaultTask() {
  // TODO handle incremental and skip the quickjs compile when incremental
  // https://docs.gradle.org/current/userguide/custom_tasks.html#incremental_tasks
  // https://docs.gradle.org/current/userguide/lazy_configuration.html#working_with_files_in_lazy_properties
  @get:Incremental
  @get:InputDirectory
  abstract val inputDir: DirectoryProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  private lateinit var quickJs: QuickJs

  @TaskAction
  fun task() {
    if (!inputDir.isPresent) {
      logger.info("inputDirectory file null")
      return
    }

    if (!outputDir.isPresent) {
      logger.info("outputDirectory file null")
      return
    }

    if (inputDir.isPresent && outputDir.isPresent) {
      val files = inputDir.files().files
      files.forEach { jsFile ->
        if (jsFile.path.endsWith(".js")) {
          val jsSourceMapFile = files.singleOrNull { smp -> smp.path == "${jsFile.path}.map" }
          // TODO name the zipline as the SHA of the source code, only compile a new file when the SHA changes
          compileFile(
            inputJs = jsFile,
            inputJsSourceMap = jsSourceMapFile,
            outputZipline = File(outputDir.get().asFile.path, jsFile.nameWithoutExtension + ".zipline")
          )
        }
      }
    }
  }

  private fun compileFile(
    inputJs: File,
    inputJsSourceMap: File?,
    outputZipline: File
  ) {
    quickJs = QuickJs.create()
    var bytecode = quickJs.use {
      quickJs.compile(inputJs.readText(), inputJs.name)
    }

    if (inputJsSourceMap != null) {
      // rewrite the bytecode with source line numbers
      bytecode = applySourceMapToBytecode(bytecode, inputJsSourceMap.readText())
    }

    val ziplineFile = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode.toByteString())

    // Use executes block then closes the sink.
    outputZipline.sink().buffer().use {
      ziplineFile.writeTo(it)
    }
  }
}
