/*
 * Copyright (C) 2023 Block, Inc.
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
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.JsIrBinary
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

/**
 * A task that produces `.js` files.
 *
 * This is used to create a [ZiplineCompileTask] that uses the produced JS as input.
 */
internal interface JsProductionTask {
  /** Like 'compileDevelopmentExecutableKotlinJsZipline'. */
  val name: String

  /** Like "js" or "blue". */
  val targetName: String

  /** Either "Webpack" or null. */
  val toolName: String?

  val mode: KotlinJsBinaryMode

  val outputFile: Provider<File>
}

/** Output of the Kotlin compiler. */
internal fun JsIrBinary.asJsProductionTask(): JsProductionTask {
  return object : JsProductionTask {
    override val name get() = linkTaskName
    override val targetName get() = target.name
    override val toolName = null
    override val mode get() = this@asJsProductionTask.mode
    override val outputFile get() = linkTask.map { it.outputFileProperty.get() }
  }
}

/** Output of the Kotlin compiler, post-processed by a Webpack toolchain. */
internal fun KotlinWebpack.asJsProductionTask(): JsProductionTask {
  return object : JsProductionTask {
    override val name = this@asJsProductionTask.name
    override val targetName = compilation.target.name
    override val toolName = "Webpack"
    override val mode = when {
      name.endsWith("DevelopmentWebpack") -> KotlinJsBinaryMode.DEVELOPMENT
      name.endsWith("ProductionWebpack") -> KotlinJsBinaryMode.PRODUCTION
      else -> error("unexpected KotlinWebpack task name: $name")
    }
    override val outputFile get() = DefaultProvider { this@asJsProductionTask.outputFile }
  }
}
