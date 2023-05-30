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

import app.cash.zipline.cli.GenerateKeyPair.Companion.NAME
import app.cash.zipline.loader.SignatureAlgorithmId
import app.cash.zipline.loader.SignatureAlgorithmId.Ed25519
import java.io.PrintStream
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
  name = NAME,
  description = [
    "Generate an Ed25519 key pair for ManifestSigner and ManifestVerifier",
  ],
  mixinStandardHelpOptions = true,
  versionProvider = Main.VersionProvider::class,
)
class GenerateKeyPair(
  private val out: PrintStream = System.out,
) : Runnable {
  @Option(
    names = ["-a", "--algorithm"],
    description = ["Signing algorithm to use."],
  )
  var algorithm: SignatureAlgorithmId = Ed25519

  @Suppress("INVISIBLE_MEMBER") // Access :zipline-loader internals.
  override fun run() {
    val keyPair = app.cash.zipline.loader.internal.generateKeyPair(algorithm)
    out.println("  ALGORITHM: $algorithm")
    out.println(" PUBLIC KEY: ${keyPair.publicKey.hex()}")
    out.println("PRIVATE KEY: ${keyPair.privateKey.hex()}")
  }

  companion object {
    const val NAME = "generate-key-pair"
  }
}
