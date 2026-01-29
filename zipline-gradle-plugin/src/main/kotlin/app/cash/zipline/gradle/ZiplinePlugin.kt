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

import app.cash.zipline.gradle.ValidateZiplineApiTask.Mode
import app.cash.zipline.loader.SignatureAlgorithmId
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.jvm.Jvm
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_UMD
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
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
    ziplineExtension.apiTracking.convention(true)

    val cliConfiguration: Configuration = target.configurations.create("ziplineCli")
      .apply {
        isCanBeConsumed = false
        isVisible = false
      }
    target.dependencies.add(
      cliConfiguration.name,
      target.ziplineDependency("zipline-cli"),
    )

    kotlinExtension.targets.withType(KotlinJsIrTarget::class.java).all { kotlinTarget ->
      kotlinTarget.compilerOptions {
        // Target latest JS to get classes, arrow functions, etc.
        this.target.set("es2015")
        // But our loader requires we still use the old module format.
        this.moduleKind.set(MODULE_UMD)
      }
      kotlinTarget.binaries.withType(JsIrBinary::class.java).all { kotlinBinary ->
        registerCompileZiplineTask(
          project = target,
          jsProductionTask = kotlinBinary.asJsProductionTask(),
          extension = ziplineExtension,
          cliConfiguration = cliConfiguration,
        )
      }
    }

    target.tasks.withType(KotlinWebpack::class.java) { kotlinWebpack ->
      if (!kotlinWebpack.name.endsWith("Webpack")) return@withType

      val jsProductionTask = kotlinWebpack.asJsProductionTask()

      val ziplineCompileTask = registerCompileZiplineTask(
        project = target,
        jsProductionTask = jsProductionTask,
        extension = ziplineExtension,
        cliConfiguration = cliConfiguration,
      )
      ziplineCompileTask.configure {
        it.dependsOn(kotlinWebpack)
      }

      val writeWebpackConfigTask = registerWriteZiplineWebpackConfig(
        project = target,
        extension = ziplineExtension,
      )
      kotlinWebpack.dependsOn(writeWebpackConfigTask)
    }

    target.afterEvaluate {
      if (ziplineExtension.apiTracking.get()) {
        val ziplineApiCheck = target.tasks.register("ziplineApiCheck")
        target.tasks.named("check").configure { checkTask ->
          checkTask.dependsOn(ziplineApiCheck)
        }

        val ziplineApiDump = target.tasks.register("ziplineApiDump")

        target.tasks.withType(KotlinCompile::class.java) { kotlinCompile ->
          if ("Test" in kotlinCompile.name) return@withType
          registerZiplineApiTask(target, kotlinCompile, cliConfiguration, Mode.Check, ziplineApiCheck)
          registerZiplineApiTask(target, kotlinCompile, cliConfiguration, Mode.Dump, ziplineApiDump)
        }
      }
    }
  }

  private fun registerCompileZiplineTask(
    project: Project,
    jsProductionTask: JsProductionTask,
    extension: ZiplineExtension,
    cliConfiguration: Configuration,
  ): TaskProvider<ZiplineCompileTask> {
    val target = (if (jsProductionTask.targetName == "js") "" else jsProductionTask.targetName)
    val mode = jsProductionTask.mode.name
    val toolName = jsProductionTask.toolName ?: ""

    // For every JS executable, create a task that compiles its .js to .zipline.
    //   input:  build/compileSync/js/main/productionExecutable/kotlin
    //   output: build/zipline/Production
    val outputDirectoryName = "${target.capitalize()}${mode.capitalize()}$toolName"
    val ziplineCompileTask = project.tasks.register(
      "${jsProductionTask.name}Zipline",
      ZiplineCompileTask::class.java,
    ) {
      it.configure(outputDirectoryName, jsProductionTask, extension, cliConfiguration)
    }

    val serveTaskName = "serve${target.capitalize()}${mode.capitalize()}${toolName}Zipline"
    project.tasks.register(serveTaskName, ZiplineServeTask::class.java) { createdTask ->
      createdTask.description = "Serves Zipline files"
      createdTask.inputDir.set(ziplineCompileTask.flatMap { it.outputDir })
      createdTask.port.set(extension.httpServerPort)
    }

    return ziplineCompileTask
  }

  private fun registerWriteZiplineWebpackConfig(
    project: Project,
    extension: ZiplineExtension,
  ): TaskProvider<out Task> {
    // Gradle doesn't have a configuration-avoidance version of maybeCreate() so try/catch instead.
    // https://github.com/gradle/gradle/issues/6243
    return try {
      project.tasks.named("writeZiplineWebpackConfig")
    } catch (e: UnknownTaskException) {
      project.tasks.register(
        "writeZiplineWebpackConfig",
        WriteWebpackConfigTask::class.java,
      ) {
        it.terserOptionsJson.set(extension.terserOptionsJson)
      }
    }
  }

  private fun registerZiplineApiTask(
    project: Project,
    compileTask: KotlinCompile,
    cliConfiguration: Configuration,
    mode: Mode,
    rollupTask: TaskProvider<Task>,
  ) {
    val task = project.tasks.register(
      // Like 'compileKotlinJvmZiplineApiCheck'
      "${compileTask.name}ZiplineApi$mode",
      ValidateZiplineApiTask::class.java,
      mode,
    )

    rollupTask.configure {
      it.dependsOn(task)
    }

    task.configure { task ->
      task.cliClasspath.from(cliConfiguration)
      task.ziplineApiFile.set(project.file("api/zipline-api.toml"))
      task.projectDirectory.set(project.projectDir.path)

      // TODO: the validation uses the wrong JDK. We should be getting the JDK from the
      //     KotlinCompile task (as defaultKotlinJavaToolchain.get().buildJvm), but it doesn't
      //     make that available for querying. Hack it to use Gradle's 'current' JVM.
      //     https://youtrack.jetbrains.com/issue/KT-59735
      val buildJvm = Jvm.current()
      task.javaHome.set(buildJvm.javaHome.path)
      task.jdkRelease.set(
        buildJvm.javaVersion?.getMajorVersion()?.toInt()
        ?: Runtime.version().feature(),
      )

      task.sourcepath.setFrom(compileTask.sources)
      task.classpath.setFrom(compileTask.libraries)
    }
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

  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline-loader internals.
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
