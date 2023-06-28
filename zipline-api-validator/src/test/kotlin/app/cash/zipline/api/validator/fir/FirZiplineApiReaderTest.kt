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

import app.cash.zipline.ZiplineService
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import org.junit.Test

internal class FirZiplineApiReaderTest {
  private val javaHome = File(System.getProperty("java.home"))
  private val jdkRelease = Runtime.version().feature()
  private val sources = System.getProperty("zipline.internal.sources")
    .split(File.pathSeparator)
    .map(::File)
    .filter(File::exists) // Entries that don't exist produce warnings.
  private val classpath = System.getProperty("zipline.internal.classpath")
    .split(File.pathSeparator)
    .map(::File)
    .filter(File::exists) // Entries that don't exist produce warnings.

  @Test
  fun happyPath() {
    val ziplineApi = readFirZiplineApi(javaHome, jdkRelease, sources, classpath)
    assertThat(ziplineApi).isEqualTo(
      FirZiplineApi(
        listOf(
          FirZiplineService(
            name = EchoService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
              FirZiplineFunction("val greeting: kotlin.String"),
              FirZiplineFunction("var terse: kotlin.Boolean"),
            ),
          ),
          FirZiplineService(
            name = ExtendedEchoService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
              FirZiplineFunction("fun echoAll(kotlin.collections.List<kotlin.String>): kotlin.collections.List<kotlin.String>"),
              FirZiplineFunction("val greeting: kotlin.String"),
              FirZiplineFunction("var terse: kotlin.Boolean"),
            ),
          ),
          FirZiplineService(
            name = ImportsJdkTypes::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun jvmIoException(java.io.IOException): kotlin.String"),
              FirZiplineFunction("fun okioIoException(okio.IOException): kotlin.String"),
            ),
          ),
          FirZiplineService(
            name = UnnecessaryEchoService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
              FirZiplineFunction("val greeting: kotlin.String"),
              FirZiplineFunction("var terse: kotlin.Boolean"),
            ),
          ),
        ),
      ),
    )
  }

  /** This should be included in the output. */
  interface EchoService : ZiplineService {
    fun echo(request: String): String
    val greeting: String
    var terse: Boolean
  }

  /** This should be included in the output. */
  interface ExtendedEchoService : EchoService {
    fun echoAll(requests: List<String>): List<String>
  }

  /** This uses externally-defined types. */
  interface ImportsJdkTypes : ZiplineService {
    /**
     * In this test we can also use a JDK class directly. This isn't the case for production code,
     * where `ZiplineService` declarations should be defined in `commonMain`.
     */
    fun jvmIoException(e: java.io.IOException): String

    /**
     * Okio's IOException is a 3rd-party class that's typealiased to a JDK class. In the generated
     * TOML file this should use the `okio.IOExeption` name, and not the name it's aliased to.
     */
    fun okioIoException(e: okio.IOException): String
  }

  /** This should be included in the output, but without additional methods. */
  interface UnnecessaryEchoService : EchoService {
    override fun echo(request: String): String
    override fun equals(other: Any?): Boolean
  }

  /** This shouldn't be included in the output. */
  @Suppress("unused")
  class SampleEchoService : EchoService {
    override val greeting: String get() = error("unexpected call")
    override var terse: Boolean
      get() = error("unexpected call")
      set(value) = error("unexpected call")

    override fun echo(request: String) = error("unexpected call")
  }
}
