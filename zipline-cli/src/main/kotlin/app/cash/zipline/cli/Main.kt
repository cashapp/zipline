/*
 * Copyright (C) 2022 Square, Inc.
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

package app.cash.zipline.cli

import app.cash.zipline.cli.Main.Companion.NAME
import kotlin.system.exitProcess
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.HelpCommand
import picocli.CommandLine.IVersionProvider
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.ParameterException
import picocli.CommandLine.Spec

@Command(
  name = NAME,
  description = ["Use Zipline without Gradle."],
  mixinStandardHelpOptions = true,
  synopsisSubcommandLabel = "COMMAND",
  subcommands = [Download::class, GenerateKeyPair::class, HelpCommand::class],
  versionProvider = Main.VersionProvider::class,
)
class Main : Runnable {
  @Spec
  lateinit var spec: CommandSpec

  @Option(names = ["--completionScript"], hidden = true)
  var completionScript: Boolean = false

  override fun run() {
    if (completionScript) {
      println(picocli.AutoComplete.bash("zipline-cli", CommandLine(Main())))
      return
    }

    throw ParameterException(spec.commandLine(), "Missing required subcommand")
  }

  class VersionProvider : IVersionProvider {
    override fun getVersion(): Array<String> {
      return arrayOf(
        "$NAME ${BuildConfig.VERSION}",
      )
    }
  }

  companion object {
    internal const val NAME = "zipline-cli"

    @JvmStatic
    fun main(args: Array<String>) {
      if (System.getProperty("javax.net.debug") == null) {
        System.setProperty("javax.net.debug", "")
      }

      exitProcess(CommandLine(Main()).execute(*args))
    }
  }
}
