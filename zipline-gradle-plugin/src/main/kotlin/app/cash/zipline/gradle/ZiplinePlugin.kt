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

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget

class ZiplinePlugin : KotlinCompilerPluginSupportPlugin {
  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = BuildConfig.KOTLIN_PLUGIN_ID

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
    groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
    artifactId = BuildConfig.KOTLIN_PLUGIN_NAME,
    version = BuildConfig.KOTLIN_PLUGIN_VERSION,
  )

  override fun getPluginArtifactForNative(): SubpluginArtifact = SubpluginArtifact(
    groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
    artifactId = BuildConfig.KOTLIN_PLUGIN_HOSTED_NAME,
    version = BuildConfig.KOTLIN_PLUGIN_VERSION,
  )

  override fun apply(project: Project) {
    super.apply(project)

    val extension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    extension.targets.withType(KotlinJsIrTarget::class.java).all { kotlinTarget ->
      kotlinTarget.binaries.withType(JsIrBinary::class.java).all { kotlinBinary ->
        registerCompileZiplineTask(project, kotlinBinary)
      }
    }
  }

  private fun registerCompileZiplineTask(project: Project, kotlinBinary: JsIrBinary) {
    // Like 'production' or 'development'.
    val modeLowercase = kotlinBinary.mode.toString().lowercase()

    // Like 'main'.
    val compilationName = kotlinBinary.compilation.name

    // Like 'compileProductionMainZipline'.
    val compileZiplineTaskName = lowerCamelCaseName(
      "compile",
      modeLowercase,
      compilationName,
      "zipline"
    )

    // Like 'productionExecutable'.
    val modeExecutable = lowerCamelCaseName(
      modeLowercase,
      "executable"
    )

    // For every JS executable, create a task that compiles its .js to .zipline.
    //    input: build/compileSync/main/productionExecutable/kotlin
    //   output: build/compileSync/main/productionExecutable/zipline
    project.tasks.register(compileZiplineTaskName, ZiplineCompileTask::class.java) { createdTask ->
      createdTask.dependsOn(kotlinBinary.linkTaskName)
      createdTask.inputDir.set(
        project.file("${project.buildDir}/compileSync/$compilationName/$modeExecutable/kotlin")
      )
      createdTask.outputDir.set(
        project.file("${project.buildDir}/compileSync/$compilationName/$modeExecutable/zipline")
      )
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
}
