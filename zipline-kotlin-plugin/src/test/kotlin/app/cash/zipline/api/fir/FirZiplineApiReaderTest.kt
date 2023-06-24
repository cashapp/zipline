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

import app.cash.zipline.ZiplineId
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
              FirZiplineFunction("annotatedFun", "fun annotatedFun(): kotlin.String"),
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
              FirZiplineFunction("annotatedVal", "val annotatedVal: kotlin.String"),
              FirZiplineFunction("val greeting: kotlin.String"),
              FirZiplineFunction("annotatedVar", "var annotatedVar: kotlin.String"),
              FirZiplineFunction("var terse: kotlin.Boolean"),
              ),
          ),
          FirZiplineService(
            name = ExtendedEchoService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("annotatedFun", "fun annotatedFun(): kotlin.String"),
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
              FirZiplineFunction("fun echoAll(kotlin.collections.List<kotlin.String>): kotlin.collections.List<kotlin.String>"),
              FirZiplineFunction("annotatedVal", "val annotatedVal: kotlin.String"),
              FirZiplineFunction("val greeting: kotlin.String"),
              FirZiplineFunction("annotatedVar", "var annotatedVar: kotlin.String"),
              FirZiplineFunction("var terse: kotlin.Boolean"),
            ),
          ),
          FirZiplineService(
            name = UnnecessaryEchoService::class.qualifiedName!!,
            functions = listOf(
              FirZiplineFunction("annotatedFunOverride", "fun annotatedFun(): kotlin.String"),
              FirZiplineFunction("fun close(): kotlin.Unit"),
              FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
              FirZiplineFunction("annotatedValOverride", "val annotatedVal: kotlin.String"),
              FirZiplineFunction("val greeting: kotlin.String"),
              FirZiplineFunction("annotatedVarOverride", "var annotatedVar: kotlin.String"),
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

    @ZiplineId("annotatedVal")
    val annotatedVal: String

    @ZiplineId("annotatedVar")
    var annotatedVar: String
    fun echo(request: String): String

    @ZiplineId("annotatedFun")
    fun annotatedFun(): String
  }

  /** This should be included in the output. */
  interface ExtendedEchoService : EchoService {
    fun echoAll(requests: List<String>): List<String>
  }

  /** This should be included in the output, but without additional methods. */
  interface UnnecessaryEchoService : EchoService {

    @ZiplineId("annotatedValOverride")
    override val annotatedVal: String

    @ZiplineId("annotatedVarOverride")
    override var annotatedVar: String

    override fun echo(request: String): String
    override fun equals(other: Any?): Boolean

    @ZiplineId("annotatedFunOverride")
    override fun annotatedFun(): String
  }

  /** This shouldn't be included in the output. */
  @Suppress("unused")
  class SampleEchoService : EchoService {
    override val greeting: String get() = error("unexpected call")
    override var terse: Boolean
      get() = error("unexpected call")
      set(value) = error("unexpected call")
    override val annotatedVal: String
      get() = error("unexpected call")
    override var annotatedVar: String
      get() = error("unexpected call")
      set(value) { error("unexpected call") }

    override fun echo(request: String) = error("unexpected call")
    override fun annotatedFun(): String = error("unexpected call")
  }
}
