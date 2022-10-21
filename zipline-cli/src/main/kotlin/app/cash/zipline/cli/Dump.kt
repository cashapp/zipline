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

import app.cash.zipline.QuickJs
import java.io.File
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
  name = "dump",
  description = ["Dump the QuickJS bytecode for a JS file"],
  mixinStandardHelpOptions = true,
  versionProvider = Main.VersionProvider::class
)
class Dump : Runnable {
  @Parameters(
    description = ["JS file"],
    arity = "1",
  )
  lateinit var file: File

  override fun run() {
    QuickJs.create().use {
      it.compile(file.readText(), file.name)
    }
  }
}
