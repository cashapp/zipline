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

import app.cash.zipline.QuickJs
import app.cash.zipline.bytecode.applySourceMapToBytecode
import app.cash.zipline.loader.CURRENT_ZIPLINE_VERSION
import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.ZiplineManifest
import app.cash.zipline.loader.ZiplineModule
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import okio.buffer
import okio.sink

class ZiplineCompiler {
  fun compile(
    inputDir: File,
    outputDir: File
  ) {
    var quickJs: QuickJs
    val modules = mutableMapOf<String, ZiplineModule>()
    val files = inputDir.listFiles()
    files!!.forEach { jsFile ->
      if (jsFile.path.endsWith(".js")) {
        val jsSourceMapFile = files.singleOrNull { sourceMap -> sourceMap.path == "${jsFile.path}.map" }
        // TODO name the zipline as the SHA of the source code, only compile a new file when the SHA changes
        val outputZiplineFilePath = jsFile.nameWithoutExtension + ".zipline"
        val outputZiplineFile = File(outputDir.path, outputZiplineFilePath)

        quickJs = QuickJs.create()
        quickJs.use {
          var bytecode = quickJs.compile(jsFile.readText(), jsFile.name)

          if (jsSourceMapFile != null) {
            // rewrite the bytecode with source line numbers
            bytecode = applySourceMapToBytecode(bytecode, jsSourceMapFile.readText())
          }
          val ziplineFile = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode.toByteString())
          // Use executes block then closes the sink.
          outputZiplineFile.sink().buffer().use {
            ziplineFile.writeTo(it)
          }

          quickJs.evaluate(COLLECT_DEPENDENCIES_DEFINE_JS, "collectDependencies.js")
          quickJs.execute(bytecode)
          val dependenciesString = quickJs
            .evaluate("globalThis.$CURRENT_MODULE_DEPENDENCIES", "getDependencies.js") as String?
          val dependencies = Json.decodeFromString<List<String>>(
            dependenciesString
            // If define is never called, dependencies is returned as null
              ?: "[]"
          )

          val ziplineSha256 = bytecode.toByteString().sha256()
          modules["./${jsFile.name}"] = ZiplineModule(
            url = outputZiplineFilePath,
            sha256 = ziplineSha256,
            dependsOnIds = dependencies
          )
        }
      }
    }
    val manifest = ZiplineManifest.create(modules)
    val manifestFile = File(outputDir.path, "manifest.zipline.json")
    manifestFile.writeText(Json.encodeToString(manifest))
  }
}

/**
 * Compile .js files to .zipline files. This is intended for internal use only; callers should
 * instead use the Gradle plugin.
 */
fun main(vararg args: String) {
  val outputDir = File(args[1])
  outputDir.mkdirs()
  ZiplineCompiler().compile(inputDir = File(args[0]), outputDir = outputDir)
}
