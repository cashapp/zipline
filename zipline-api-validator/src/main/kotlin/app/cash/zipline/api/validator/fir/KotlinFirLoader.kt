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
package app.cash.zipline.api.validator.fir

import java.io.File
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.EXCEPTION
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.LOGGING
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.moduleChunk
import org.jetbrains.kotlin.cli.common.modules.ModuleChunk
import org.jetbrains.kotlin.cli.diagnosticFactoriesStorage
import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.pipeline.ConfigurationPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFrontendPipelinePhase
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.StandardFileSystems
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.MessageCollectorAccess
import org.jetbrains.kotlin.diagnostics.KtRegisteredDiagnosticFactoriesStorage
import org.jetbrains.kotlin.diagnostics.impl.DiagnosticsCollectorImpl
import org.jetbrains.kotlin.fir.pipeline.AllModulesFrontendOutput

/**
 * Loads classes using the compiler tools into the Frontend Intermediate Representation (FIR), so
 * they can be inspected.
 *
 * Note that this class is copied from Redwood.
 * https://github.com/cashapp/redwood/blob/afe1c9f5f95eec3cff46837a4b2749cbaf72af8b/redwood-tooling-schema/src/main/kotlin/app/cash/redwood/tooling/schema/schemaParserFir.kt#L28
 */
internal class KotlinFirLoader(
  private val javaHome: File,
  private val jdkRelease: Int,
  private val sources: Collection<File>,
  private val classpath: Collection<File>,
) : AutoCloseable {
  private val disposable = Disposer.newDisposable()

  private val messageCollector = object : MessageCollector {
    override fun clear() = Unit
    override fun hasErrors() = false

    override fun report(
      severity: CompilerMessageSeverity,
      message: String,
      location: CompilerMessageSourceLocation?,
    ) {
      val destination = when (severity) {
        LOGGING -> null
        EXCEPTION, ERROR -> System.err
        else -> System.out
      }
      destination?.println(message)
    }
  }

  /**
   * @param targetName an opaque identifier for this operation.
   */
  @OptIn(
    CompilerConfiguration.Internals::class,
    MessageCollectorAccess::class,
  )
  fun load(targetName: String): AllModulesFrontendOutput {
    val configuration = CompilerConfiguration()
    configuration.put(CommonConfigurationKeys.MODULE_NAME, targetName)
    configuration.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
    configuration.put(CommonConfigurationKeys.USE_FIR, true)
    configuration.put(JVMConfigurationKeys.JDK_HOME, javaHome)
    configuration.put(JVMConfigurationKeys.JDK_RELEASE, jdkRelease)
    configuration.addKotlinSourceRoots(sources.map { it.absolutePath })
    configuration.addJvmClasspathRoots(classpath.toList())
    @OptIn(ExperimentalCompilerApi::class)
    configuration.extensionsStorage = CompilerPluginRegistrar.ExtensionStorage()
    configuration.diagnosticFactoriesStorage = KtRegisteredDiagnosticFactoriesStorage()
    configuration.moduleChunk = ModuleChunk(emptyList())
    configuration.diagnosticsCollector = DiagnosticsCollectorImpl()

    val frontendOutput = JvmFrontendPipelinePhase.executePhase(
      ConfigurationPipelineArtifact(
        configuration = configuration,
        rootDisposable = disposable,
      ),
    ) ?: throw IllegalStateException("Failed to run compiler frontend phase")

    return frontendOutput.frontendOutput
  }

  override fun close() {
    disposable.dispose()
  }
}
