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
import app.cash.zipline.loader.ManifestSigner
import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.ZiplineManifest
import app.cash.zipline.loader.internal.MANIFEST_FILE_NAME
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import okio.HashingSink
import okio.buffer
import okio.sink

object ZiplineCompiler {
  private const val MODULE_PATH_PREFIX = "./"
  private const val ZIPLINE_EXTENSION = ".zipline"

  fun compile(
    inputDir: File,
    outputDir: File,
    mainFunction: String?,
    mainModuleId: String?,
    manifestSigner: ManifestSigner?,
    version: String?,
  ) {
    val modules = mutableMapOf<String, ZiplineManifest.Module>()
    val jsFiles = getJsFiles(inputDir.listFiles()!!.asList())
    jsFiles.forEach { jsFile -> compileSingleFile(jsFile, outputDir, modules) }
    writeManifest(
      outputDir = outputDir,
      mainFunction = mainFunction,
      mainModuleId = mainModuleId,
      manifestSigner = manifestSigner,
      modules = modules,
      version = version,
    )
  }

  fun incrementalCompile(
    outputDir: File,
    modifiedFiles: List<File>,
    addedFiles: List<File>,
    removedFiles: List<File>,
    mainFunction: String?,
    mainModuleId: String?,
    manifestSigner: ManifestSigner?,
    version: String?,
  ) {
    val modifiedFileNames = getJsFiles(modifiedFiles).map { it.name }.toSet()
    val removedFileNames = getJsFiles(removedFiles).map { it.name }.toSet()

    val modules = mutableMapOf<String, ZiplineManifest.Module>()

    // Get the current manifest and remove any removed or modified modules
    val manifestFile = File(outputDir.path, MANIFEST_FILE_NAME)
    val manifest = Json.decodeFromString<ZiplineManifest>(manifestFile.readText())
    modules.putAll(manifest.modules.filter { (k, _) ->
      val moduleFileName = k.removePrefix(MODULE_PATH_PREFIX)
      moduleFileName !in removedFileNames && moduleFileName !in modifiedFileNames
    })

    // Delete Zipline files for any removed JS files
    removedFileNames.forEach { File(outputDir.path + "/" + it.removeSuffix(".js") + ZIPLINE_EXTENSION).delete()}

    // Compile the newly added or modified files and add them into the module list
    val addedOrModifiedFiles = getJsFiles(addedFiles) + getJsFiles(modifiedFiles)
    addedOrModifiedFiles.forEach { file -> compileSingleFile(file, outputDir, modules) }

    // Write back a new up-to-date manifest
    writeManifest(
      outputDir = outputDir,
      mainFunction = mainFunction,
      mainModuleId = mainModuleId,
      manifestSigner = manifestSigner,
      modules = modules,
      version = version,
    )
  }

  private fun compileSingleFile(
    jsFile: File,
    outputDir: File,
    modules: MutableMap<String, ZiplineManifest.Module>
  ) {
    val jsSourceMapFile = File("${jsFile.path}.map")
    val outputZiplineFilePath = jsFile.nameWithoutExtension + ZIPLINE_EXTENSION
    val outputZiplineFile = File(outputDir.path, outputZiplineFilePath)

    val quickJs = QuickJs.create()
    quickJs.use {
      var bytecode = quickJs.compile(jsFile.readText(), jsFile.name)

      if (jsSourceMapFile.exists()) {
        // rewrite the bytecode with source line numbers
        bytecode = applySourceMapToBytecode(bytecode, jsSourceMapFile.readText())
      }
      val ziplineFile = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode.toByteString())
      val sha256 = outputZiplineFile.sink().use { fileSink ->
        val hashingSink = HashingSink.sha256(fileSink)
        hashingSink.buffer().use {
          ziplineFile.writeTo(it)
        }
        hashingSink.hash
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

      modules["$MODULE_PATH_PREFIX${jsFile.name}"] = ZiplineManifest.Module(
        url = outputZiplineFilePath,
        sha256 = sha256,
        dependsOnIds = dependencies
      )
    }
  }

  private fun writeManifest(
    outputDir: File,
    mainFunction: String? = null,
    mainModuleId: String? = null,
    manifestSigner: ManifestSigner? = null,
    modules: MutableMap<String, ZiplineManifest.Module>,
    version: String? = null,
  ) {
    val unsignedManifest = ZiplineManifest.create(
      modules = modules,
      mainFunction = mainFunction,
      mainModuleId = mainModuleId,
      version = version,
    )

    val manifest = manifestSigner?.sign(unsignedManifest) ?: unsignedManifest

    val manifestFile = File(outputDir.path, MANIFEST_FILE_NAME)
    manifestFile.writeText(Json.encodeToString(manifest))
  }

  private fun getJsFiles(files: List<File>) = files.filter { it.path.endsWith(".js") }
}

/**
 * Compile .js files to .zipline files. This is intended for internal use only; callers should
 * instead use the Gradle plugin.
 */
fun main(vararg args: String) {
  val argsList = args.toMutableList()

  val inputDir = File(argsList.removeFirst())
  val outputDir = File(argsList.removeFirst())
  outputDir.mkdirs()
  ZiplineCompiler.compile(
    inputDir = inputDir,
    outputDir = outputDir,
    mainFunction = argsList.removeFirstOrNull(),
    mainModuleId = argsList.removeFirstOrNull(),
    manifestSigner = null,
    version = argsList.removeFirstOrNull(),
  )
}
