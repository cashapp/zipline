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
import app.cash.zipline.ZiplineManifest
import app.cash.zipline.loader.CURRENT_ZIPLINE_VERSION
import app.cash.zipline.loader.ZiplineFile
import assertk.assertThat
import assertk.assertions.startsWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.serialization.json.Json
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Before
import org.junit.Test

class ZiplineCompilerTest {
  private val quickJs = QuickJs.create()

  @Before
  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
  fun setUp() {
    // Configure QuickJS to support module loading.
    app.cash.zipline.internal.initModuleLoader(quickJs)
  }

  @After
  fun after() {
    quickJs.close()
  }

  @Test
  fun `write to and read from zipline`() {
    val moduleNameToFile = compile("src/test/resources/happyPath/", true)
    for ((moduleName, ziplineFile) in moduleNameToFile) {
      loadJsModule(quickJs, moduleName, ziplineFile.quickjsBytecode.toByteArray())
    }

    val exception = assertFailsWith<Exception> {
      quickJs.evaluate("require('./hello.js').sayHello()", "test.js")
    }
    // .kt files in the stacktrace means that the sourcemap was applied correctly.
    assertThat(exception.stackTraceToString()).startsWith(
      """
      |app.cash.zipline.QuickJsException: boom!
      |	at JavaScript.goBoom1(throwException.kt)
      |	at JavaScript.goBoom2(throwException.kt:9)
      |	at JavaScript.goBoom3(throwException.kt:6)
      |	at JavaScript.sayHello(throwException.kt:3)
      |	at JavaScript.<eval>(test.js)
      |
      """.trimMargin(),
    )
  }

  @Test
  fun `no source map`() {
    val moduleNameToFile = compile("src/test/resources/happyPathNoSourceMap/", false)
    for ((_, ziplineFile) in moduleNameToFile) {
      quickJs.execute(ziplineFile.quickjsBytecode.toByteArray())
    }
    assertEquals("Hello, guy!", quickJs.evaluate("greet('guy')", "test.js"))
  }

  @Test
  fun `js with imports and exports`() {
    val moduleNameToFile = compile("src/test/resources/jsWithImportsExports/", false)
    for ((name, ziplineFile) in moduleNameToFile) {
      loadJsModule(quickJs, name, ziplineFile.quickjsBytecode.toByteArray())
    }
  }

  @Test
  fun `incremental compile`() {
    val rootProject = "src/test/resources/incremental"
    val outputDir = File("$rootProject/base/build/zipline")

    // Clean up dir from previous runs
    if (outputDir.exists()) {
      outputDir.deleteRecursively()
    }

    // Start with base compile to generate manifest and starting files
    compile("$rootProject/base", false)

    val moduleNameToFile = assertZiplineIncrementalCompile(
      "$rootProject/base",
      addedFiles = File("$rootProject/added").listFiles()!!.asList(),
      modifiedFiles = File("$rootProject/modified").listFiles()!!.asList(),
      removedFiles = File("$rootProject/removed").listFiles()!!.asList(),
    )
    for ((_, ziplineFile) in moduleNameToFile) {
      quickJs.execute(ziplineFile.quickjsBytecode.toByteArray())
    }

    // Jello file was removed
    assertFalse(File("$outputDir/jello.zipline").exists())
    // Bello file was added
    quickJs.execute(readZiplineFile(File("$outputDir/bello.zipline")).quickjsBytecode.toByteArray())
    assertEquals("Bello!", quickJs.evaluate("bello()", "test.js"))
    // Hello file was replaced with bonjour
    quickJs.execute(readZiplineFile(File("$outputDir/hello.zipline")).quickjsBytecode.toByteArray())
    assertEquals("Bonjour, guy!", quickJs.evaluate("greet('guy')", "test.js"))
    // Yello file remains untouched
    quickJs.execute(readZiplineFile(File("$outputDir/yello.zipline")).quickjsBytecode.toByteArray())
    assertEquals("HELLO", quickJs.evaluate("greet()", "test.js"))
  }

  private fun readZiplineFile(file: File): ZiplineFile {
    val result = file.source().buffer().use { source ->
      ZiplineFile.read(source)
    }
    assertEquals(CURRENT_ZIPLINE_VERSION, result.ziplineVersion)
    return result
  }

  private fun compile(
    rootProject: String,
    dirHasSourceMaps: Boolean,
  ): Map<String, ZiplineFile> {
    val inputDir = File("$rootProject/jsBuild")
    val outputDir = File("$rootProject/build/zipline")
    outputDir.mkdirs()

    val mainModuleId = "./app.js"
    val mainFunction = "zipline.ziplineMain"
    ZiplineCompiler(
      outputDir = outputDir,
      mainFunction = mainFunction,
      mainModuleId = mainModuleId,
      manifestSigner = null,
      version = null,
      metadata = mapOf(),
      stripLineNumbers = false,
    ).compile(
      inputDir = inputDir,
    )

    val expectedNumberFiles = if (dirHasSourceMaps) inputDir.listFiles()!!.size / 2 else inputDir.listFiles()!!.size
    // Don't include Zipline manifest
    val actualNumberFiles = (outputDir.listFiles()?.size ?: 0) - 1
    assertEquals(expectedNumberFiles, actualNumberFiles)

    return getCompileResult(outputDir, mainModuleId, mainFunction)
  }

  private fun assertZiplineIncrementalCompile(
    rootProject: String,
    modifiedFiles: List<File>,
    addedFiles: List<File>,
    removedFiles: List<File>,
  ): Map<String, ZiplineFile> {
    val inputDir = File("$rootProject/jsBuild")
    val outputDir = File("$rootProject/build/zipline")
    outputDir.mkdirs()

    val mainModuleId = "./app.js"
    val mainFunction = "zipline.ziplineMain"
    ZiplineCompiler(
      outputDir = outputDir,
      mainFunction = mainFunction,
      mainModuleId = mainModuleId,
      manifestSigner = null,
      version = null,
      metadata = mapOf(),
      stripLineNumbers = false,
    ).incrementalCompile(
      modifiedFiles = modifiedFiles,
      addedFiles = addedFiles,
      removedFiles = removedFiles,
    )

    val expectedNumberFiles = inputDir.listFiles()!!.size + addedFiles.size - removedFiles.size
    // Don't include Zipline manifest
    val actualNumberFiles = (outputDir.listFiles()?.size ?: 0) - 1
    assertEquals(expectedNumberFiles, actualNumberFiles)

    return getCompileResult(outputDir, mainModuleId, mainFunction)
  }

  private fun getCompileResult(
    outputDir: File,
    mainModuleId: String,
    mainFunction: String,
  ): Map<String, ZiplineFile> {
    // Load and parse manifest
    val manifestFile = File(outputDir, "manifest.zipline.json")
    val manifestString = manifestFile.readText()
    val manifest = Json.decodeFromString<ZiplineManifest>(manifestString)

    // Confirm that mainModuleId and mainFunction have been added to the manifest
    assertEquals(mainModuleId, manifest.mainModuleId)
    assertEquals(mainFunction, manifest.mainFunction)

    // Confirm that all manifest files are present in outputDir
    outputDir.listFiles()!!.forEach { ziplineFile ->
      manifest.modules.keys.contains(ziplineFile.path)
    }

    // Iterate over files by Manifest order
    val result = mutableMapOf<String, ZiplineFile>()
    for ((key, module) in manifest.modules) {
      result[key] = readZiplineFile(File(outputDir, module.url))
    }
    return result
  }

  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
  private fun loadJsModule(quickJs: QuickJs, id: String, bytecode: ByteArray) {
    return app.cash.zipline.internal.loadJsModule(quickJs, id, bytecode)
  }
}
