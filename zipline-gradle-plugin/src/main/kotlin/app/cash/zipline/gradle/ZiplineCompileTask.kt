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

import app.cash.zipline.loader.ManifestSigner
import java.io.File
import javax.inject.Inject
import okio.ByteString.Companion.decodeHex
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

/**
 * Compiles `.js` files to `.zipline` files.
 *
 * This also writes a Webpack configuration file `webpack.config.d/zipline.js`. in the directory
 * required by the Kotlin Gradle plugin.
 *
 * https://kotlinlang.org/docs/js-project-setup.html#webpack-configuration-file
 * https://webpack.js.org/concepts/configuration/
 */
abstract class ZiplineCompileTask @Inject constructor(
  objectFactory: ObjectFactory
) : DefaultTask() {

  @get:Incremental
  @get:InputDirectory
  val inputDir: DirectoryProperty = objectFactory.directoryProperty()

  @get:OutputDirectory
  val outputDir: DirectoryProperty = objectFactory.directoryProperty()

  @Optional
  @get:Input
  val mainModuleId: Property<String> = objectFactory.property(String::class.java)

  @Optional
  @get:Input
  val mainFunction: Property<String> = objectFactory.property(String::class.java)

  @get:Input
  val signingKeys: NamedDomainObjectContainer<ManifestSigningKey> =
    objectFactory.domainObjectContainer(ManifestSigningKey::class.java)

  @Optional
  @get:Input
  val version: Property<String> = objectFactory.property(String::class.java)

  @TaskAction
  fun task(inputChanges: InputChanges) {
    val inputDirFile = inputDir.get().asFile
    val outputDirFile = outputDir.get().asFile
    val mainModuleId = mainModuleId.orNull
    val mainFunction = mainFunction.orNull
    val manifestSigner = when {
      signingKeys.isNotEmpty() -> {
        val builder = ManifestSigner.Builder()
        for ((name, manifestSigningKey) in signingKeys.asMap) {
          builder.addEd25519(name, manifestSigningKey.privateKeyHex.decodeHex())
        }
        builder.build()
      }
      else -> null
    }
    val version = version.orNull

    if (inputChanges.isIncremental) {
      fun filterByChangeType(filter: (ChangeType) -> Boolean): List<File> {
        return inputChanges.getFileChanges(inputDir)
          .filter { filter(it.changeType) }
          .map { outputDir.file(it.normalizedPath).get().asFile }
      }

      ZiplineCompiler.incrementalCompile(
        outputDir = outputDirFile,
        modifiedFiles = filterByChangeType { changeType ->  changeType == ChangeType.MODIFIED },
        addedFiles = filterByChangeType { changeType ->  changeType == ChangeType.ADDED },
        removedFiles = filterByChangeType { changeType ->  changeType == ChangeType.REMOVED },
        mainFunction = mainFunction,
        mainModuleId = mainModuleId,
        manifestSigner = manifestSigner,
        version = version,
      )
    } else {
      ZiplineCompiler.compile(
        inputDir = inputDirFile,
        outputDir = outputDirFile,
        mainFunction = mainFunction,
        mainModuleId = mainModuleId,
        manifestSigner = manifestSigner,
        version = version,
      )
    }
  }
}
