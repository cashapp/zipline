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
import app.cash.zipline.api.validator.toml.TomlZiplineApi
import app.cash.zipline.api.validator.toml.TomlZiplineFunction
import app.cash.zipline.api.validator.toml.TomlZiplineService

sealed interface ApiCompatibilityDecision

class ActualApiHasProblems(
  val messages: List<String>,
) : ApiCompatibilityDecision

class ExpectedApiRequiresUpdates(
  val updatedApi: TomlZiplineApi,
) : ApiCompatibilityDecision

object ExpectedApiIsUpToDate : ApiCompatibilityDecision

/** Compare the expected and actual API and decide what to do about it. */
fun makeApiCompatibilityDecision(
  expectedApi: TomlZiplineApi,
  actualApi: FirZiplineApi,
): ApiCompatibilityDecision {
  val problemMessages = mutableListOf<String>()
  var hasChanges = false

  val actualServices = actualApi.services.associateBy { it.name }
  if (actualServices.size != expectedApi.services.size) hasChanges = true

  for (expectedService in expectedApi.services) {
    val serviceName = expectedService.name
    val actualService = actualServices[serviceName]

    if (actualService == null) {
      problemMessages +=
        """
        |Expected service not found:
        |  $serviceName
        """.trimMargin()
      continue
    }

    val actualFunctions = actualService.functions.associateBy { it.id }
    if (actualFunctions.size != expectedService.functions.size) hasChanges = true

    for (expectedFunction in expectedService.functions) {
      val functionId = expectedFunction.id
      val actualFunction = actualFunctions[functionId]
      if (actualFunction == null) {
        val comment = expectedFunction.leadingComment
        problemMessages += buildString {
          append("Expected function $functionId of $serviceName not found")
          if (comment.isNotEmpty()) {
            append(":\n  ")
            append(comment.replace("\n", "\n  "))
          }
        }
      }
    }
  }

  return when {
    problemMessages.isNotEmpty() -> ActualApiHasProblems(problemMessages)
    hasChanges -> ExpectedApiRequiresUpdates(actualApi.toToml())
    else -> ExpectedApiIsUpToDate
  }
}

private fun FirZiplineApi.toToml(): TomlZiplineApi {
  return TomlZiplineApi(
    services.map { service ->
      TomlZiplineService(
        name = service.name,
        functions = service.functions.map { function ->
          TomlZiplineFunction(
            leadingComment = function.signature,
            id = function.id,
          )
        },
      )
    },
  )
}
