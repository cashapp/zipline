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
import app.cash.zipline.bytecode.SourceMap
import app.cash.zipline.bytecode.applySourceMapToBytecode
import app.cash.zipline.bytecode.removeLeadingDotDots
import app.cash.zipline.internal.collectModuleDependencies
import app.cash.zipline.internal.getModuleDependencies
import app.cash.zipline.loader.CURRENT_ZIPLINE_VERSION
import app.cash.zipline.loader.ManifestSigner
import app.cash.zipline.loader.ZiplineFile
import app.cash.zipline.loader.ZiplineManifest
import app.cash.zipline.loader.internal.MANIFEST_FILE_NAME
import app.cash.zipline.loader.internal.fetcher.encodeToString
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import okio.HashingSink
import okio.buffer
import okio.sink

internal object ZiplineCompiler {
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
    val jsFiles = getJsFiles(inputDir.listFiles()!!.asList())
    val modules = compileFilesInParallel(jsFiles, outputDir)
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

    // Get the current manifest and remove any removed or modified modules
    val manifestFile = File(outputDir.path, MANIFEST_FILE_NAME)
    val manifest = Json.decodeFromString<ZiplineManifest>(manifestFile.readText())
    val unchangedModules = manifest.modules.filter { (k, _) ->
      val moduleFileName = k.removePrefix(MODULE_PATH_PREFIX)
      moduleFileName !in removedFileNames && moduleFileName !in modifiedFileNames
    }

    // Delete Zipline files for any removed JS files
    removedFileNames.forEach { File(outputDir.path + "/" + it.removeSuffix(".js") + ZIPLINE_EXTENSION).delete()}

    // Compile the newly added or modified files and add them into the module list
    val addedOrModifiedFiles = getJsFiles(addedFiles) + getJsFiles(modifiedFiles)
    val compiledModules = compileFilesInParallel(addedOrModifiedFiles, outputDir)

    // Write back a new up-to-date manifest
    writeManifest(
      outputDir = outputDir,
      mainFunction = mainFunction,
      mainModuleId = mainModuleId,
      manifestSigner = manifestSigner,
      modules = unchangedModules + compiledModules,
      version = version,
    )
  }

  private fun compileFilesInParallel(
    files: List<File>,
    outputDir: File,
  ) = runBlocking {
    files
      .map { file ->
        async(Dispatchers.Default) {
          compileSingleFile(file, outputDir)
        }
      }
      .awaitAll()
      .toMap()
  }

  private fun compileSingleFile(
    jsFile: File,
    outputDir: File,
  ): Pair<String, ZiplineManifest.Module> {
    val jsSourceMapFile = File("${jsFile.path}.map")
    val outputZiplineFilePath = jsFile.nameWithoutExtension + ZIPLINE_EXTENSION
    val outputZiplineFile = File(outputDir.path, outputZiplineFilePath)

    val quickJs = QuickJs.create()
    quickJs.use {
      var bytecode = quickJs.compile(jsFile.readText(), jsFile.name)

      if (jsSourceMapFile.exists()) {
        // rewrite the bytecode with source line numbers.
        val sourceMap = SourceMap.parse(jsSourceMapFile.readText()).removeLeadingDotDots()
        bytecode = applySourceMapToBytecode(bytecode, sourceMap)
      }
      val ziplineFile = ZiplineFile(CURRENT_ZIPLINE_VERSION, bytecode.toByteString())
      val sha256 = outputZiplineFile.sink().use { fileSink ->
        val hashingSink = HashingSink.sha256(fileSink)
        hashingSink.buffer().use {
          ziplineFile.writeTo(it)
        }
        hashingSink.hash
      }

      quickJs.collectModuleDependencies()
      quickJs.execute(bytecode)
      val dependencies = quickJs.getModuleDependencies()

      return "$MODULE_PATH_PREFIX${jsFile.name}" to ZiplineManifest.Module(
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
    modules: Map<String, ZiplineManifest.Module>,
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
    manifestFile.writeText(manifest.encodeToString())
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
