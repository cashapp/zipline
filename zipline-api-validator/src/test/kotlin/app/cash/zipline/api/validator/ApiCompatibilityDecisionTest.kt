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
package app.cash.zipline.api.validator

import app.cash.zipline.api.validator.fir.FirZiplineApi
import app.cash.zipline.api.validator.fir.FirZiplineFunction
import app.cash.zipline.api.validator.fir.FirZiplineService
import app.cash.zipline.api.validator.fir.signatureHash
import app.cash.zipline.api.validator.toml.TomlZiplineApi
import app.cash.zipline.api.validator.toml.TomlZiplineFunction
import app.cash.zipline.api.validator.toml.TomlZiplineService
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.Test

class ApiCompatibilityDecisionTest {
  @Test
  fun actualServiceIsMissing() {
    val expectedApi = TomlZiplineApi(
      listOf(
        TomlZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            TomlZiplineFunction(
              "fun echo(kotlin.String): kotlin.String",
              "fun echo(kotlin.String): kotlin.String".signatureHash(),
            ),
          ),
        ),
      ),
    )
    val actualApi = FirZiplineApi(listOf())

    val decision = makeApiCompatibilityDecision(expectedApi, actualApi) as ActualApiHasProblems
    assertThat(decision.messages).containsExactly(
      """
      |Expected service not found:
      |  com.example.EchoService
      """.trimMargin(),
    )
  }

  @Test
  fun actualFunctionIsMissing() {
    val expectedApi = TomlZiplineApi(
      listOf(
        TomlZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            TomlZiplineFunction(
              "fun echo(kotlin.String): kotlin.String",
              "fun echo(kotlin.String): kotlin.String".signatureHash(),
            ),
            TomlZiplineFunction(
              "fun echoTwo(kotlin.String): kotlin.String",
              "fun echoTwo(kotlin.String): kotlin.String".signatureHash(),
            ),
          ),
        ),
      ),
    )
    val actualApi = FirZiplineApi(
      listOf(
        FirZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
          ),
        ),
      ),
    )

    val decision = makeApiCompatibilityDecision(expectedApi, actualApi) as ActualApiHasProblems
    assertThat(decision.messages).containsExactly(
      """
      |Expected function AFSfjQyQ of com.example.EchoService not found:
      |  fun echoTwo(kotlin.String): kotlin.String
      """.trimMargin(),
    )
  }

  @Test
  fun serviceAdded() {
    val expectedApi = TomlZiplineApi(listOf())
    val actualApi = FirZiplineApi(
      listOf(
        FirZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
          ),
        ),
      ),
    )

    val decision = makeApiCompatibilityDecision(expectedApi, actualApi)
      as ExpectedApiRequiresUpdates
    assertThat(decision.updatedApi).isEqualTo(
      TomlZiplineApi(
        listOf(
          TomlZiplineService(
            name = "com.example.EchoService",
            functions = listOf(
              TomlZiplineFunction(
                "fun echo(kotlin.String): kotlin.String",
                "fun echo(kotlin.String): kotlin.String".signatureHash(),
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun functionAdded() {
    val expectedApi = TomlZiplineApi(
      listOf(
        TomlZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            TomlZiplineFunction(
              "fun echo(kotlin.String): kotlin.String",
              "fun echo(kotlin.String): kotlin.String".signatureHash(),
            ),
          ),
        ),
      ),
    )

    val actualApi = FirZiplineApi(
      listOf(
        FirZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
            FirZiplineFunction("fun echoTwo(kotlin.String): kotlin.String"),
          ),
        ),
      ),
    )

    val decision = makeApiCompatibilityDecision(expectedApi, actualApi)
      as ExpectedApiRequiresUpdates
    assertThat(decision.updatedApi).isEqualTo(
      TomlZiplineApi(
        listOf(
          TomlZiplineService(
            name = "com.example.EchoService",
            functions = listOf(
              TomlZiplineFunction(
                "fun echo(kotlin.String): kotlin.String",
                "fun echo(kotlin.String): kotlin.String".signatureHash(),
              ),
              TomlZiplineFunction(
                "fun echoTwo(kotlin.String): kotlin.String",
                "fun echoTwo(kotlin.String): kotlin.String".signatureHash(),
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun expectedApiIsUpToDate() {
    val expectedApi = TomlZiplineApi(
      listOf(
        TomlZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            TomlZiplineFunction(
              "fun echo(kotlin.String): kotlin.String",
              "fun echo(kotlin.String): kotlin.String".signatureHash(),
            ),
          ),
        ),
      ),
    )

    val actualApi = FirZiplineApi(
      listOf(
        FirZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
          ),
        ),
      ),
    )

    val decision = makeApiCompatibilityDecision(expectedApi, actualApi)
    assertThat(decision).isEqualTo(ExpectedApiIsUpToDate)
  }

  /**
   * Confirm [ActualApiHasProblems] takes precedence over [ExpectedApiRequiresUpdates] when both
   * apply because the service names are disjoint.
   */
  @Test
  fun differentServicesDecidesActualApiHasProblems() {
    val expectedApi = TomlZiplineApi(
      listOf(
        TomlZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            TomlZiplineFunction(
              "fun echo(kotlin.String): kotlin.String",
              "fun echo(kotlin.String): kotlin.String".signatureHash(),
            ),
          ),
        ),
      ),
    )

    val actualApi = FirZiplineApi(
      listOf(
        FirZiplineService(
          name = "com.example.HelloService",
          functions = listOf(
            FirZiplineFunction("fun echo(kotlin.String): kotlin.String"),
          ),
        ),
      ),
    )

    val decision = makeApiCompatibilityDecision(expectedApi, actualApi)
    assertThat(decision).isInstanceOf<ActualApiHasProblems>()
  }

  /**
   * Confirm [ActualApiHasProblems] takes precedence over [ExpectedApiRequiresUpdates] when both
   * apply because the functions are disjoint.
   */
  @Test
  fun differentFunctionsDecidesActualApiHasProblems() {
    val expectedApi = TomlZiplineApi(
      listOf(
        TomlZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            TomlZiplineFunction(
              "fun echo(kotlin.String): kotlin.String",
              "fun echo(kotlin.String): kotlin.String".signatureHash(),
            ),
          ),
        ),
      ),
    )

    val actualApi = FirZiplineApi(
      listOf(
        FirZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            FirZiplineFunction("fun echoTwo(kotlin.String): kotlin.String"),
          ),
        ),
      ),
    )

    val decision = makeApiCompatibilityDecision(expectedApi, actualApi)
    assertThat(decision).isInstanceOf<ActualApiHasProblems>()
  }

  /**
   * Although we have the function signature in both forms, only the function ID matters. This makes
   * it possible to rename functions or their arguments without breaking compatibility.
   *
   * See https://github.com/cashapp/zipline/issues/1048
   */
  @Test
  fun functionSignaturesMayDisagreeWithoutError() {
    val expectedApi = TomlZiplineApi(
      listOf(
        TomlZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            TomlZiplineFunction(
              "fun echo(kotlin.String): kotlin.String",
              "fun echo(kotlin.String): kotlin.String".signatureHash(),
            ),
          ),
        ),
      ),
    )

    val actualApi = FirZiplineApi(
      listOf(
        FirZiplineService(
          name = "com.example.EchoService",
          functions = listOf(
            FirZiplineFunction(
              "fun echo(kotlin.String): kotlin.String".signatureHash(),
              // Renamed function!
              "fun renamedFromEcho(kotlin.String): kotlin.String",
            ),
          ),
        ),
      ),
    )

    val decision = makeApiCompatibilityDecision(expectedApi, actualApi)
    assertThat(decision).isEqualTo(ExpectedApiIsUpToDate)
  }
}
