/*
 * Copyright (C) 2022 Block, Inc.
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

import assertk.assertThat
import assertk.assertions.isNull
import assertk.assertions.matches
import com.github.ajalt.clikt.core.NoSuchOption
import java.io.PrintStream
import kotlin.test.assertFailsWith
import okio.Buffer
import org.junit.Test

class GenerateKeyPairTest {
  private val systemOut = Buffer()

  @Test fun happyPathDefault() {
    runCommand()
    assertThat(systemOut.readUtf8Line()!!).matches(Regex("  ALGORITHM: Ed25519"))
    assertThat(systemOut.readUtf8Line()!!).matches(Regex(" PUBLIC KEY: [\\da-f]{64}"))
    assertThat(systemOut.readUtf8Line()!!).matches(Regex("PRIVATE KEY: [\\da-f]{64}"))
    assertThat(systemOut.readUtf8Line()).isNull()
  }

  @Test fun happyPathEd25519() {
    runCommand("-a", "Ed25519")
    assertThat(systemOut.readUtf8Line()!!).matches(Regex("  ALGORITHM: Ed25519"))
    assertThat(systemOut.readUtf8Line()!!).matches(Regex(" PUBLIC KEY: [\\da-f]{64}"))
    assertThat(systemOut.readUtf8Line()!!).matches(Regex("PRIVATE KEY: [\\da-f]{64}"))
    assertThat(systemOut.readUtf8Line()).isNull()
  }

  @Test fun happyPathEcdsaP256() {
    runCommand("-a", "EcdsaP256")
    assertThat(systemOut.readUtf8Line()!!).matches(Regex("  ALGORITHM: EcdsaP256"))
    assertThat(systemOut.readUtf8Line()!!).matches(Regex(" PUBLIC KEY: [\\da-f]{130}"))
    // Expected lengths were determined experimentally!
    assertThat(systemOut.readUtf8Line()!!).matches(Regex("PRIVATE KEY: [\\da-f]{134}"))
    assertThat(systemOut.readUtf8Line()).isNull()
  }

  @Test fun unmatchedArgument() {
    assertFailsWith<NoSuchOption> {
      runCommand("-unexpected")
    }
  }

  private fun runCommand(vararg args: String) {
    val command = GenerateKeyPair(PrintStream(systemOut.outputStream()))
    command.parse(args.toList())
  }
}
