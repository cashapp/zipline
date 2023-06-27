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
@file:JvmName("Main")

package app.cash.zipline.cli

import app.cash.zipline.cli.ValidateZiplineApi.Companion.NAME_CHECK
import app.cash.zipline.cli.ValidateZiplineApi.Companion.NAME_DUMP
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption

fun main(vararg args: String) {
  if (System.getProperty("javax.net.debug") == null) {
    System.setProperty("javax.net.debug", "")
  }

  NoOpCliktCommand()
    .subcommands(
      Download(),
      GenerateKeyPair(),
      ValidateZiplineApi(NAME_CHECK),
      ValidateZiplineApi(NAME_DUMP),
    )
    .versionOption(BuildConfig.VERSION)
    .main(args)
}
