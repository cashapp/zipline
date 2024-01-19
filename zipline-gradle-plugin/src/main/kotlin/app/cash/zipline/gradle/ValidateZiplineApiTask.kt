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

import app.cash.zipline.gradle.ValidateZiplineApiTask.Mode
import java.io.File
import javax.inject.Inject
import okio.Buffer
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

@Suppress("unused") // Public API for Gradle plugin users.
abstract class ValidateZiplineApiTask @Inject constructor(
  fileCollectionFactory: FileCollectionFactory,
  @Input val mode: Mode,
  private val workerExecutor: WorkerExecutor,
) : DefaultTask() {
  @get:Classpath
  abstract val cliClasspath: ConfigurableFileCollection

  @get:OutputFile
  abstract val ziplineApiFile: RegularFileProperty

  @get:Input
  abstract val projectDirectory: Property<String>

  @get:Input
  abstract val javaHome: Property<String>

  @get:Input
  abstract val jdkRelease: Property<Int>

  @get:InputFiles
  internal val sourcepath = fileCollectionFactory.configurableFiles("sourcepath")

  @get:Classpath
  internal val classpath = fileCollectionFactory.configurableFiles("classpath")

  init {
    when (mode) {
      Mode.Check -> {
        group = "verification"
        description = "Confirm that the current Zipline API matches the expectations file"
      }
      Mode.Dump -> {
        description = "Write the current Zipline APIs to the expectations file"
      }
    }
  }

  @TaskAction
  fun task() {
    val tomlFile = ziplineApiFile.get().asFile
    val projectDirectory = projectDirectory.get()
    val tomlFileRelative = tomlFile.relativeTo(File(projectDirectory))
    val queue = workerExecutor.noIsolation()
    queue.submit(ZiplineApiValidatorWorker::class.java) {
      it.cliClasspath.from(cliClasspath)
      it.mode.set(mode)
      it.projectDirectory.set(projectDirectory)
      it.tomlFile.set(tomlFileRelative)
      it.javaHome.set(File(javaHome.get()))
      it.jdkRelease.set(jdkRelease.get())
      it.sources.setFrom(sourcepath)
      it.classpath.setFrom(classpath)
    }
  }

  enum class Mode {
    Check,
    Dump,
  }
}

private interface ZiplineApiValidatorParameters : WorkParameters {
  val cliClasspath: ConfigurableFileCollection
  val mode: Property<Mode>
  val projectDirectory: Property<String>
  val tomlFile: RegularFileProperty
  val javaHome: RegularFileProperty
  val jdkRelease: Property<Int>
  val sources: ConfigurableFileCollection
  val classpath: ConfigurableFileCollection
}

private abstract class ZiplineApiValidatorWorker @Inject constructor(
  private val execOperations: ExecOperations,
) : WorkAction<ZiplineApiValidatorParameters> {
  override fun execute() {
    val errorOutput = Buffer()
    val execResult = execOperations.javaexec { exec ->
      val workingDirectory = File(parameters.projectDirectory.get())

      exec.isIgnoreExitValue = true
      exec.classpath = parameters.cliClasspath
      exec.mainClass.set("app.cash.zipline.cli.Main")
      exec.errorOutput = errorOutput.outputStream()
      exec.workingDir = workingDirectory

      val subcommand = when (parameters.mode.get()) {
        Mode.Check -> "zipline-api-check"
        Mode.Dump -> "zipline-api-dump"
      }
      exec.args = listOf(
        subcommand,
        "--toml-file",
        parameters.tomlFile.get().asFile.toRelativeString(workingDirectory),
        "--java-home",
        parameters.javaHome.get().asFile.toString(),
        "--jdk-release",
        parameters.jdkRelease.get().toString(),
        "--sources",
        parameters.sources.files.joinToString(File.pathSeparator),
        "--class-path",
        parameters.classpath.files.joinToString(File.pathSeparator),
        "--dump-command-name",
        ":ziplineApiDump",
      )
    }

    check(execResult.exitValue == 0) {
      errorOutput.readUtf8()
    }

    print(errorOutput.readUtf8())
  }
}
