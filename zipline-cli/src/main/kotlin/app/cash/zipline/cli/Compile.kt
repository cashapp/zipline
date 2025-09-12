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
package app.cash.zipline.cli

import app.cash.zipline.loader.ManifestSigner
import app.cash.zipline.loader.SignatureAlgorithmId
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import okio.ByteString.Companion.decodeHex

internal class Compile : CliktCommand("compile") {
  override fun help(context: Context) = "Compile .js files to .zipline files"

  private val inputDir by option("--input").file().required()
    .help("Directory from which .js files will be loaded.")

  private val outputDir by option("--output").file().required()
    .help("Directory into which .zipline files will be written.")

  private val addedFiles by option("--added").file().multiple()
    .help("Input files added since the last compile. This will trigger incremental compilation.")
  private val removedFiles by option("--removed").file().multiple()
    .help("Input files removed since the last compile. This will trigger incremental compilation.")
  private val modifiedFiles by option("--modified").file().multiple()
    .help("Input files modified since the last compile. This will trigger incremental compilation.")

  private val mainFunction by option()
  private val mainModuleId by option()
  private val version by option()
  private val stripLineNumbers by option().flag()

  private val signingKeys by option("--sign")
    .help(
      """
      |Key to sign outputs.
      |
      |Format: "id:name:key" where 'id' is one of ${SignatureAlgorithmId.entries} and 'key' is a hex-encoded private key.
      """.trimMargin(),
    )
    .convert {
      val (idName, name, keyHex) = it.split(':', limit = 3)
      val id = SignatureAlgorithmId.valueOf(idName)
      val key = keyHex.decodeHex()
      Triple(id, name, key)
    }
    .multiple()

  private val metadata by option().associate()

  override fun run() {
    val manifestSigner = if (signingKeys.isEmpty()) {
      null
    } else {
      ManifestSigner.Builder()
        .apply {
          for ((id, name, key) in signingKeys) {
            add(id, name, key)
          }
        }
        .build()
    }

    val compiler = ZiplineCompiler(
      outputDir = outputDir,
      mainFunction = mainFunction,
      mainModuleId = mainModuleId,
      manifestSigner = manifestSigner,
      version = version,
      metadata = metadata,
      stripLineNumbers = stripLineNumbers,
    )

    if (addedFiles.size or removedFiles.size or modifiedFiles.size != 0) {
      compiler.incrementalCompile(
        modifiedFiles = modifiedFiles,
        addedFiles = addedFiles,
        removedFiles = removedFiles,
      )
    } else {
      compiler.compile(inputDir)
    }
  }
}
