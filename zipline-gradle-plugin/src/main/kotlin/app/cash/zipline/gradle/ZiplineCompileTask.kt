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

import app.cash.zipline.loader.SignatureAlgorithmId
import java.io.Serializable
import javax.inject.Inject
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.ChangeType.ADDED
import org.gradle.work.ChangeType.MODIFIED
import org.gradle.work.ChangeType.REMOVED
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode

/**
 * Compiles `.js` files to `.zipline` files.
 */
abstract class ZiplineCompileTask @Inject constructor(
  private val execOperations: ExecOperations,
) : DefaultTask() {

  @get:Incremental
  @get:InputDirectory
  abstract val inputDir: DirectoryProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Optional
  @get:Input
  abstract val mainModuleId: Property<String>

  @get:Optional
  @get:Input
  abstract val mainFunction: Property<String>

  @get:Input
  abstract val signingKeys: ListProperty<ManifestSigningKey>

  @get:Optional
  @get:Input
  abstract val version: Property<String>

  @get:Optional
  @get:Input
  abstract val metadata: MapProperty<String, String>

  @get:Optional
  @get:Input
  abstract val stripLineNumbers: Property<Boolean>

  @get:Classpath
  abstract val classpath: ConfigurableFileCollection

  internal fun configure(
    outputDirectoryName: String,
    jsProductionTask: JsProductionTask,
    extension: ZiplineExtension,
    cliConfiguration: Configuration,
  ) {
    description = "Compile .js to .zipline"

    classpath.setFrom(cliConfiguration)

    inputDir.fileProvider(jsProductionTask.outputFile.map { it.asFile.parentFile })

    outputDir.set(project.layout.buildDirectory.dir("zipline/$outputDirectoryName"))
    mainModuleId.set(extension.mainModuleId)
    mainFunction.set(extension.mainFunction)
    version.set(extension.version)
    metadata.set(extension.metadata)

    // Only ever strip line numbers in production builds.
    stripLineNumbers.set(
      extension.stripLineNumbers.map {
        it && jsProductionTask.mode == KotlinJsBinaryMode.PRODUCTION
      },
    )

    signingKeys.set(
      project.provider {
      extension.signingKeys.asMap.values
    }.flatMap {
      it.map { dslKey ->
        dslKey.privateKeyHex.zip(dslKey.algorithmId) { privateKeyHex, algorithmId ->
          ManifestSigningKey(dslKey.name, algorithmId, privateKeyHex.decodeHex())
        }
      }.flatten()
    },
    )
  }

  @TaskAction
  fun task(inputChanges: InputChanges) {
    val args = buildList {
      add("compile")
      add("--input")
      add(inputDir.get().asFile.toString())
      add("--output")
      add(outputDir.get().asFile.toString())
      mainModuleId.orNull?.let {
        add("--main-module-id")
        add(it)
      }
      mainFunction.orNull?.let {
        add("--main-function")
        add(it)
      }
      for (signingKey in signingKeys.get()) {
        add("--sign")
        add(
          buildString {
          append(signingKey.algorithm.name)
          append(':')
          append(signingKey.name)
          append(':')
          append(signingKey.privateKey.hex())
        },
        )
      }
      version.orNull?.let {
        add("--version")
        add(it)
      }
      metadata.orNull?.let {
        for ((key, value) in it) {
          add("--metadata")
          add("$key=$value")
        }
      }
      if (stripLineNumbers.getOrElse(false)) {
        add("--strip-line-numbers")
      }
      if (inputChanges.isIncremental) {
        for (fileChange in inputChanges.getFileChanges(inputDir)) {
          add(
            when (fileChange.changeType) {
            ADDED -> "--added"
            MODIFIED -> "--modified"
            REMOVED -> "--removed"
          },
          )
          add(fileChange.file.toString())
        }
      }
    }

    execOperations.javaexec { exec ->
      exec.classpath = classpath
      exec.mainClass.set("app.cash.zipline.cli.Main")
      exec.setArgs(args)
    }
  }

  data class ManifestSigningKey(
    val name: String,
    val algorithm: SignatureAlgorithmId,
    val privateKey: ByteString,
  ) : Serializable
}
