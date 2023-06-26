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
package app.cash.zipline.cli

import app.cash.zipline.api.validator.ActualApiHasProblems
import app.cash.zipline.api.validator.ExpectedApiIsUpToDate
import app.cash.zipline.api.validator.ExpectedApiRequiresUpdates
import app.cash.zipline.api.validator.fir.readFirZiplineApi
import app.cash.zipline.api.validator.makeApiCompatibilityDecision
import app.cash.zipline.api.validator.toml.TomlZiplineApi
import app.cash.zipline.api.validator.toml.readTomlZiplineApi
import app.cash.zipline.api.validator.toml.writeTomlZiplineApi
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import java.io.File
import kotlin.io.path.exists
import okio.buffer
import okio.sink
import okio.source

/**
 * Compares a Kotlin compilation's actual declared APIs against expected APIs and reports potential
 * problems.
 *
 * Actual APIs are extracted by running the frontend of the Kotlin compiler (FIR).
 *
 * Expected APIs are persisted as TOML files.
 *
 * Which command is executed decides what happens when the actual API (.kt files) declares more
 * services or functions than expected (.toml file):
 *
 *  * zipline-api-check fails the build.
 *  * zipline-api-dump updates the TOML file.
 *
 * Both modes fail the build if the converse is true; ie. the actual API declares fewer services
 * or functions than expected.
 *
 * Both modes succeed if the actual APIs are equal to the expectations.
 */
class ValidateZiplineApi(
  name: String,
) : CliktCommand(name = name) {
  private val tomlFile by option("--toml-file")
    .path()
    .required()
    .help("Path to the TOML file that this command will read and write")

  private val sources by option("--sources")
    .convert { it.split(File.pathSeparator).map(::File) }
    .required()

  private val classpath by option("--class-path")
    .convert { it.split(File.pathSeparator).map(::File) }
    .required()

  override fun run() {
    val expectedZiplineApi = when {
      tomlFile.exists() -> tomlFile.source().buffer().use { it.readTomlZiplineApi() }
      else -> TomlZiplineApi(listOf())
    }

    val actualZiplineApi = readFirZiplineApi(sources, classpath)

    when (val decision = makeApiCompatibilityDecision(expectedZiplineApi, actualZiplineApi)) {
      is ActualApiHasProblems -> {
        throw CliktError(
          """
          |Zipline API has compatibility problems:
          |  ${decision.messages.joinToString(separator = "\n").replace("\n", "\n  ")}
          """.trimMargin(),
        )
      }

      is ExpectedApiRequiresUpdates -> {
        when (commandName) {
          NAME_CHECK -> {
            throw CliktError(
              """
              |Zipline API file is incomplete. Run $NAME_DUMP to update it.
              |  $tomlFile
              """.trimMargin(),
            )
          }

          NAME_DUMP -> {
            tomlFile.sink().buffer().use { it.writeTomlZiplineApi(decision.updatedApi) }
            echo("Updated $tomlFile because Zipline API has changed")
          }

          else -> throw CliktError("unexpected command name: $commandName")
        }
      }

      ExpectedApiIsUpToDate -> {
        // Success.
      }
    }
  }

  companion object {
    const val NAME_CHECK = "zipline-api-check"
    const val NAME_DUMP = "zipline-api-dump"
  }
}
