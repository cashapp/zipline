/*
 * Copyright (C) 2020 Brian Norman
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

package com.bnorm.template

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@AutoService(CommandLineProcessor::class)
class TemplateCommandLineProcessor : CommandLineProcessor {
  companion object {
    private const val OPTION_STRING = "string"
    private const val OPTION_FILE = "file"

    val ARG_STRING = CompilerConfigurationKey<String>(OPTION_STRING)
    val ARG_FILE = CompilerConfigurationKey<String>(OPTION_FILE)
  }

  override val pluginId: String = BuildConfig.KOTLIN_PLUGIN_ID

  override val pluginOptions: Collection<CliOption> = listOf(
    CliOption(
      optionName = OPTION_STRING,
      valueDescription = "string",
      description = "sample string argument",
      required = false,
    ),
    CliOption(
      optionName = OPTION_FILE,
      valueDescription = "file",
      description = "sample file argument",
      required = false,
    ),
  )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration
  ) {
    return when (option.optionName) {
      OPTION_STRING -> configuration.put(ARG_STRING, value)
      OPTION_FILE -> configuration.put(ARG_FILE, value)
      else -> throw IllegalArgumentException("Unexpected config option ${option.optionName}")
    }
  }
}
