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

import app.cash.zipline.loader.internal.generateKeyPair
import java.io.File
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.slf4j.LoggerFactory

class ZiplinePlugin : KotlinCompilerPluginSupportPlugin {
  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = BuildConfig.KOTLIN_PLUGIN_ID

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
    groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
    artifactId = BuildConfig.KOTLIN_PLUGIN_NAME,
    version = BuildConfig.KOTLIN_PLUGIN_VERSION,
  )

  override fun apply(target: Project) {
    super.apply(target)

    createGenerateKeyPairTask(target)

    val extension = target.extensions.findByType(KotlinMultiplatformExtension::class.java)
      ?: return

    extension.targets.withType(KotlinJsIrTarget::class.java).all { kotlinTarget ->
      kotlinTarget.binaries.withType(JsIrBinary::class.java).all { kotlinBinary ->
        registerCompileZiplineTask(target, kotlinBinary)
      }
    }
  }

  private fun registerCompileZiplineTask(project: Project, kotlinBinary: JsIrBinary) {
    // Like 'compileDevelopmentExecutableKotlinJsZipline'.
    val linkTaskName = kotlinBinary.linkTaskName
    val compileZiplineTaskName = "${linkTaskName}Zipline"

    // For every JS executable, create a task that compiles its .js to .zipline.
    //    input: build/compileSync/main/productionExecutable/kotlin
    //   output: build/compileSync/main/productionExecutable/kotlinZipline
    val ziplineCompileTask = project.tasks.register(compileZiplineTaskName, ZiplineCompileTask::class.java) { createdTask ->
      createdTask.description = "Compile .js to .zipline"

      val linkOutputFolderProvider = kotlinBinary.linkTask.map { File(it.kotlinOptions.outputFile!!).parentFile }
      createdTask.inputDir.fileProvider(linkOutputFolderProvider)
      createdTask.outputDir.fileProvider(linkOutputFolderProvider.map { it.parentFile.resolve("${it.name}Zipline") })
    }

    val target = if (kotlinBinary.target.name == "js") "" else kotlinBinary.target.name
    val capitalizedMode = kotlinBinary.mode.name
      .lowercase(locale = Locale.US)
      .replaceFirstChar { it.titlecase(locale = Locale.US) }
    val serveTaskName = "serve${target}${capitalizedMode}Zipline"
    project.tasks.register(serveTaskName, ZiplineServeTask::class.java) { createdTask ->
      createdTask.description = "Serves Zipline files"
      createdTask.inputDir = ziplineCompileTask.map { it.outputDir }
    }
  }

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project

    // Configure Kotlin JS to generate modular JS files
    project.tasks.withType(KotlinJsCompile::class.java).configureEach { task ->
      task.kotlinOptions {
        freeCompilerArgs += listOf("-Xir-per-module")
      }
    }
    return project.provider {
      listOf() // No options.
    }
  }

  private fun createGenerateKeyPairTask(project: Project) {
    project.tasks.register("generateZiplineManifestKeyPair") { task ->
      task.doLast {
        val logger = LoggerFactory.getLogger(ZiplinePlugin::class.java)
        val keyPair = generateKeyPair()
        logger.warn("---------------- ----------------------------------------------------------------")
        logger.warn("     PUBLIC KEY: ${keyPair.publicKey.hex()}")
        logger.warn("    PRIVATE KEY: ${keyPair.privateKey.hex()}")
        logger.warn("---------------- ----------------------------------------------------------------")
      }
    }
  }
}
