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
package app.cash.zipline.gradle

import app.cash.zipline.loader.SignatureAlgorithmId
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class ZiplineExtension {
  abstract val mainModuleId: Property<String>
  abstract val mainFunction: Property<String>
  abstract val version: Property<String>
  abstract val signingKeys: NamedDomainObjectContainer<SigningKey>
  abstract val httpServerPort: Property<Int>
  abstract val metadata: MapProperty<String, String>

  /**
   * True to strip line number information from the encoded QuickJS bytecode in production builds.
   * Line numbers will not be included in stack traces. This is false by default.
   */
  abstract val stripLineNumbers: Property<Boolean>

  /**
   * JSON-encoded options for the Webpack Terser plugin that is applied to production builds. The
   * interpretation of the JSON is specified by the Terser tool.
   *
   * If null, the default configuration is used.
   *
   * https://github.com/terser/terser#minify-options
   */
  abstract val terserOptionsJson: Property<String>

  /**
   * Configure production builds to get a small artifact, by removing information used for stack
   * traces and the sampling profiler.
   *
   * Note that this is not necessarily the most-compact configuration. It is merely a compact
   * configuration that we recommend. This configuration may change between Zipline releases.
   *
   * The current implementation retains file name information but discards class and function names.
   */
  fun optimizeForSmallArtifactSize() {
    stripLineNumbers.set(true)
    terserOptionsJson.set(
      """
      |{
      |  compress: {
      |    sequences: false,
      |  },
      |  mangle: {
      |  },
      |  format: {
      |    beautify: true,
      |    braces: true,
      |  }
      |}
      """.trimMargin(),
    )
  }

  /**
   * Configure production builds to get good stack traces and profiling, at a cost of artifact size.
   */
  fun optimizeForDeveloperExperience() {
    stripLineNumbers.set(false)
    terserOptionsJson.set(
      """
      |{
      |  compress: {
      |    sequences: false,
      |  },
      |  mangle: false,
      |  format: {
      |    beautify: true,
      |    braces: true,
      |  }
      |}
      """.trimMargin(),
    )
  }

  abstract class SigningKey(val name: String) {
    abstract val algorithmId: Property<SignatureAlgorithmId>
    abstract val privateKeyHex: Property<String>
  }
}
