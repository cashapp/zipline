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

package app.cash.zipline.gradle

import app.cash.zipline.api.compatibility.ActualApiHasProblems
import app.cash.zipline.api.compatibility.ExpectedApiIsUpToDate
import app.cash.zipline.api.compatibility.ExpectedApiRequiresUpdates
import app.cash.zipline.api.compatibility.makeApiCompatibilityDecision
import app.cash.zipline.api.fir.readFirZiplineApi
import app.cash.zipline.api.toml.TomlZiplineApi
import app.cash.zipline.api.toml.readZiplineApi
import app.cash.zipline.api.toml.writeZiplineApi
import javax.inject.Inject
import okio.buffer
import okio.sink
import okio.source
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@Suppress("unused") // Public API for Gradle plugin users.
abstract class ZiplineApiDumpTask @Inject constructor(
  fileCollectionFactory: FileCollectionFactory,
) : DefaultTask() {

  @get:OutputFile
  abstract val ziplineApiFile: RegularFileProperty

  @get:InputFiles
  internal val sourcepath = fileCollectionFactory.configurableFiles("sourcepath")

  @get:Classpath
  internal val classpath = fileCollectionFactory.configurableFiles("classpath")

  @TaskAction
  fun task() {
    val tomlFile = ziplineApiFile.get().asFile

    val expectedZiplineApi = when {
      tomlFile.exists() -> tomlFile.source().buffer().use { it.readZiplineApi() }
      else -> TomlZiplineApi(listOf())
    }

    val actualZiplineApi = readFirZiplineApi(sourcepath.files, classpath.files)

    when (val decision = makeApiCompatibilityDecision(expectedZiplineApi, actualZiplineApi)) {
      is ActualApiHasProblems -> {
        throw Exception(
          """
          |Zipline API has compatibility problems:
          |  ${decision.messages.joinToString(separator = "\n").replace("\n", "\n  ") }
          """.trimMargin(),
        )
      }

      is ExpectedApiRequiresUpdates -> {
        tomlFile.sink().buffer().use { it.writeZiplineApi(decision.updatedApi) }
      }

      ExpectedApiIsUpToDate -> {
        // Do nothing.
      }
    }
  }
}
