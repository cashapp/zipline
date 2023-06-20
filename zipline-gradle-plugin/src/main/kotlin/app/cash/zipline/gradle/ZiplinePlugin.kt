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
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import org.slf4j.LoggerFactory

@Suppress("unused") // Created reflectively by Gradle.
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

    createGenerateKeyPairTasks(target)

    val kotlinExtension = target.extensions.findByType(KotlinMultiplatformExtension::class.java)
      ?: return

    val ziplineExtension = target.extensions.create("zipline", ZiplineExtension::class.java)

    kotlinExtension.targets.withType(KotlinJsIrTarget::class.java).all { kotlinTarget ->
      kotlinTarget.binaries.withType(JsIrBinary::class.java).all { kotlinBinary ->
        registerCompileZiplineTask(
          project = target,
          jsProductionTask = kotlinBinary.asJsProductionTask(),
          extension = ziplineExtension,
        )
      }
    }

    target.tasks.withType(KotlinWebpack::class.java) { kotlinWebpack ->
      if (!kotlinWebpack.name.endsWith("Webpack")) {
        return@withType
      }
      val ziplineCompileTask = registerCompileZiplineTask(
        project = target,
        jsProductionTask = kotlinWebpack.asJsProductionTask(),
        extension = ziplineExtension,
      )
      ziplineCompileTask.configure {
        it.dependsOn(kotlinWebpack)
      }
    }

    target.tasks.withType(KotlinCompile::class.java) { kotlinCompile ->
      if (kotlinCompile.name == "compileKotlinJvm") {
        registerZiplineApiDumpTask(target, kotlinCompile)
      }
    }
  }

  private fun registerCompileZiplineTask(
    project: Project,
    jsProductionTask: JsProductionTask,
    extension: ZiplineExtension,
  ): TaskProvider<ZiplineCompileTask> {
    // For every JS executable, create a task that compiles its .js to .zipline.
    //   input: build/compileSync/js/main/productionExecutable/kotlin
    //   output: build/compileSync/js/main/productionExecutable/kotlinZipline
    val ziplineCompileTask = project.tasks.register(
      "${jsProductionTask.name}Zipline",
      ZiplineCompileTask::class.java,
    )
    ziplineCompileTask.configure {
      it.configure(jsProductionTask, extension)
    }

    val target = (if (jsProductionTask.targetName == "js") "" else jsProductionTask.targetName)
    val mode = jsProductionTask.mode.name
    val toolName = jsProductionTask.toolName ?: ""
    val serveTaskName = "serve${target.capitalize()}${mode.capitalize()}${toolName}Zipline"
    project.tasks.register(serveTaskName, ZiplineServeTask::class.java) { createdTask ->
      createdTask.description = "Serves Zipline files"
      createdTask.inputDir.set(ziplineCompileTask.flatMap { it.outputDir })
      createdTask.port.set(extension.httpServerPort)
    }

    return ziplineCompileTask
  }

  private fun registerZiplineApiDumpTask(
    project: Project,
    kotlinCompileTool: KotlinCompileTool,
  ): TaskProvider<ZiplineApiDumpTask> {
    val result = project.tasks.register(
      "ziplineApiDump",
      ZiplineApiDumpTask::class.java,
    )

    result.configure {
      it.ziplineApiFile.set(project.file("api/zipline-api.toml"))
      it.sourcepath.setFrom(kotlinCompileTool.sources)
      it.classpath.setFrom(kotlinCompileTool.libraries)
    }

    return result
  }

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>,
  ): Provider<List<SubpluginOption>> {
    return kotlinCompilation.target.project.provider {
      listOf() // No options.
    }
  }

  private fun createGenerateKeyPairTasks(project: Project) {
    project.tasks.register("generateZiplineManifestKeyPairEd25519") { task ->
      task.doLast {
        generateKeyPair(SignatureAlgorithmId.Ed25519)
      }
    }
    project.tasks.register("generateZiplineManifestKeyPairEcdsaP256") { task ->
      task.doLast {
        generateKeyPair(SignatureAlgorithmId.EcdsaP256)
      }
    }
  }

  @Suppress("INVISIBLE_MEMBER") // Access :zipline-loader internals.
  private fun generateKeyPair(algorithm: SignatureAlgorithmId) {
    val logger = LoggerFactory.getLogger(ZiplinePlugin::class.java)
    val keyPair = app.cash.zipline.loader.internal.generateKeyPair(algorithm)
    logger.warn("---------------- ----------------------------------------------------------------")
    logger.warn("      ALGORITHM: $algorithm")
    logger.warn("     PUBLIC KEY: ${keyPair.publicKey.hex()}")
    logger.warn("    PRIVATE KEY: ${keyPair.privateKey.hex()}")
    logger.warn("---------------- ----------------------------------------------------------------")
  }
}
