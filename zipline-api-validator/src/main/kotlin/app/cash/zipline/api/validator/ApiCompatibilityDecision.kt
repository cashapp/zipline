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
import app.cash.zipline.api.validator.toml.TomlZiplineConstant
import app.cash.zipline.api.validator.toml.TomlZiplineFunction
import app.cash.zipline.api.validator.toml.TomlZiplineService

sealed interface ApiCompatibilityDecision

data class ApiCompatibilityOptions(
  val forbidServiceExtension: Boolean = false,
  val includeSchemaInFunctionIds: Boolean = false,
  val includeApiConstants: Boolean = false,
)

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
  options: ApiCompatibilityOptions = ApiCompatibilityOptions(),
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

    val expectedFunctions = expectedService.functions.associateBy { it.id }
    val actualFunctions = actualService.functions.associateBy { it.id }
    val expectedFunctionsBySignature = expectedService.functions
      .filter { it.leadingComment.isNotEmpty() }
      .associateBy { it.leadingComment }
    val addedFunctions = actualService.functions.filter { it.id !in expectedFunctions }
    if (addedFunctions.isNotEmpty()) {
      if (options.forbidServiceExtension) {
        problemMessages += addedFunctions.map { addedFunction ->
          buildString {
            append(
              """
              |New function is found in $serviceName:
              |  ${addedFunction.signature.replace("\n", "\n  ")}
              |Service extension is forbidden. Create a new Zipline service instead.
              """.trimMargin(),
            )
            val expectedFunctionWithSameSignature = expectedFunctionsBySignature[addedFunction.signature]
            if (expectedFunctionWithSameSignature != null && options.includeSchemaInFunctionIds) {
              append("\n")
              append(schemaIdChangedHint(expectedFunctionWithSameSignature.id))
            }
          }
        }
      } else {
        hasChanges = true
      }
    }

    for (expectedFunction in expectedService.functions) {
      val functionId = expectedFunction.id
      val actualFunction = actualFunctions[functionId]
      if (actualFunction == null) {
        val comment = expectedFunction.leadingComment
        val actualFunctionWithSameSignature = actualService.functions
          .takeIf { options.includeSchemaInFunctionIds && comment.isNotEmpty() }
          ?.firstOrNull { it.signature == comment }
        problemMessages += buildString {
          append("Expected function $functionId of $serviceName not found")
          if (comment.isNotEmpty()) {
            append(":\n  ")
            append(comment.replace("\n", "\n  "))
          }
          if (actualFunctionWithSameSignature != null) {
            append("\n")
            append(schemaIdChangedHint(actualFunctionWithSameSignature.id))
          }
        }
      }
    }

    val expectedConstants = expectedService.constants.associateBy { it.id }
    val actualConstants = actualService.constants.associateBy { it.id }
    val addedConstants = actualService.constants.filter { it.id !in expectedConstants }
    if (addedConstants.isNotEmpty()) {
      hasChanges = true
    }

    for (expectedConstant in expectedService.constants) {
      val constantId = expectedConstant.id
      val actualConstant = actualConstants[constantId]
      if (actualConstant == null) {
        val comment = expectedConstant.leadingComment
        problemMessages += buildString {
          append("Expected constant $constantId of $serviceName not found")
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
    hasChanges -> ExpectedApiRequiresUpdates(actualApi.toToml(options))
    else -> ExpectedApiIsUpToDate
  }
}

private fun schemaIdChangedHint(otherFunctionId: String): String {
  return """
    |Hint: a function with the same signature exists with ID $otherFunctionId.
    |Since includeSchemaInFunctionIds is enabled, this probably means a DTO used by this function, or one of its nested DTOs, changed.
  """.trimMargin()
}

private fun FirZiplineApi.toToml(options: ApiCompatibilityOptions): TomlZiplineApi {
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
        constants = if (options.includeApiConstants) {
          service.constants.map { constant ->
            TomlZiplineConstant(
              leadingComment = constant.signature,
              id = constant.id,
            )
          }
        } else {
          listOf()
        },
      )
    },
  )
}
