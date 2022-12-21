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

import app.cash.zipline.gradle.ZiplineCompileTask.ManifestSigningKey
import app.cash.zipline.loader.SignatureAlgorithmId
import app.cash.zipline.loader.internal.generateKeyPair
import java.io.File
import java.util.Locale
import okio.ByteString.Companion.decodeHex
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

    val extension = target.extensions.findByType(KotlinMultiplatformExtension::class.java)
      ?: return

    val configuration = target.extensions.create("zipline", ZiplineExtension::class.java)

    extension.targets.withType(KotlinJsIrTarget::class.java).all { kotlinTarget ->
      kotlinTarget.binaries.withType(JsIrBinary::class.java).all { kotlinBinary ->
        registerCompileZiplineTask(target, kotlinBinary, configuration)
      }
    }
  }

  private fun registerCompileZiplineTask(
    project: Project,
    kotlinBinary: JsIrBinary,
    configuration: ZiplineExtension,
  ) {
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

      createdTask.mainModuleId.set(configuration.mainModuleId)
      createdTask.mainFunction.set(configuration.mainFunction)
      createdTask.version.set(configuration.version)

      fun <T> Iterable<Provider<T>>.flatten(): Provider<List<T>> {
        val empty = project.provider { emptyList<T>() }
        return fold(empty) { listProvider, elementProvider ->
          listProvider.zip(elementProvider, Collection<T>::plus)
        }
      }

      createdTask.signingKeys.set(project.provider {
        configuration.signingKeys.asMap.values
      }.flatMap {
        it.map { dslKey ->
          dslKey.privateKeyHex.zip(dslKey.algorithmId) { privateKeyHex, algorithmId ->
            ManifestSigningKey(dslKey.name, algorithmId, privateKeyHex.decodeHex())
          }
        }.flatten()
      })
    }

    val target = if (kotlinBinary.target.name == "js") "" else kotlinBinary.target.name
    val capitalizedMode = kotlinBinary.mode.name
      .lowercase(locale = Locale.US)
      .replaceFirstChar { it.titlecase(locale = Locale.US) }
    val serveTaskName = "serve${target}${capitalizedMode}Zipline"
    project.tasks.register(serveTaskName, ZiplineServeTask::class.java) { createdTask ->
      createdTask.description = "Serves Zipline files"
      createdTask.inputDir.set(ziplineCompileTask.flatMap { it.outputDir })
      createdTask.port.set(configuration.httpServerPort)
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

  private fun generateKeyPair(algorithm: SignatureAlgorithmId) {
    val logger = LoggerFactory.getLogger(ZiplinePlugin::class.java)
    val keyPair = algorithm.generateKeyPair()
    logger.warn("---------------- ----------------------------------------------------------------")
    logger.warn("      ALGORITHM: $algorithm")
    logger.warn("     PUBLIC KEY: ${keyPair.publicKey.hex()}")
    logger.warn("    PRIVATE KEY: ${keyPair.privateKey.hex()}")
    logger.warn("---------------- ----------------------------------------------------------------")
  }
}
