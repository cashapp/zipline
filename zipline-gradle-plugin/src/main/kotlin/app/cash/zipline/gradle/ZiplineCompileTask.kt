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

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode

/**
 * Compiles `.js` files to `.zipline` files.
 *
 * This also writes a Webpack configuration file `webpack.config.d/zipline.js`. in the directory
 * required by the Kotlin Gradle plugin.
 *
 * https://kotlinlang.org/docs/js-project-setup.html#webpack-configuration-file
 * https://webpack.js.org/concepts/configuration/
 */
abstract class ZiplineCompileTask : DefaultTask() {
  @get:Input
  lateinit var compilationName: String

  @get:Input
  lateinit var mode: KotlinJsBinaryMode

  // Like 'productionExecutable'.
  private val modeExecutable: String by lazy {
    lowerCamelCaseName(mode.toString().lowercase(), "executable")
  }

  // TODO handle incremental and skip the quickjs compile when incremental
  // https://docs.gradle.org/current/userguide/custom_tasks.html#incremental_tasks
  // https://docs.gradle.org/current/userguide/lazy_configuration.html#working_with_files_in_lazy_properties
  // @get:Incremental
  @get:InputDirectory
  val inputDir: File by lazy {
    project.file("${project.buildDir}/compileSync/$compilationName/$modeExecutable/kotlin")
  }

  @get:OutputDirectory
  val outputDir: File by lazy {
    project.file("${project.buildDir}/compileSync/$compilationName/$modeExecutable/zipline")
  }

  @get:OutputFile
  val webpackConfigFile: File by lazy {
    project.projectDir.resolve(configFilePath)
  }

  @TaskAction
  fun task() {
    ZiplineCompiler.compile(inputDir, outputDir)

    val webpackHome = project.rootDir.resolve("build/js/packages/placeholder-name")
    val directory = outputDir.relativeTo(webpackHome)
    webpackConfigFile.parentFile.mkdirs()
    webpackConfigFile.writeText(
      """
      |// DO NOT EDIT.
      |//
      |// This file is generated by the Zipline Gradle plugin. You may ignore it
      |// by adding this line to your .gitignore:
      |//
      |// generated-zipline-webpack-config.js
      |//
      |//
      |if (config.devServer) {
      |  // Accept calls from any device. Typically this is a phone on the same WiFi network.
      |  config.devServer.host = "0.0.0.0";
      |
      |  // Don't inject served JavaScript with live reloading features.
      |  config.devServer.liveReload = false;
      |  config.devServer.hot = false;
      |  config.devServer.client = false;
      |
      |  // Don't open Chrome.
      |  delete config.devServer.open;
      |
      |  // Serve .zipline files. Note that the directory changes for development vs. production.
      |  config.devServer.static.push('$directory');
      |}
      """.trimMargin()
    )
  }

  companion object {
    const val configFilePath = "webpack.config.d/generated-zipline-webpack-config.js"
  }
}
