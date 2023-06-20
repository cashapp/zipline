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
package app.cash.zipline.api.fir

import app.cash.zipline.ZiplineService
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import org.junit.Test

internal class FirZiplineApiReaderTest {
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
    val ziplineApi = readFirZiplineApi(sources, classpath)
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
    val greeting: String
    var terse: Boolean
    fun echo(request: String): String
  }

  /** This should be included in the output. */
  interface ExtendedEchoService : EchoService {
    fun echoAll(requests: List<String>): List<String>
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
