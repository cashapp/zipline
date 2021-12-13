/*
 * Copyright (C) 2021 Square, Inc.
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
package app.cash.zipline

import app.cash.zipline.internal.bridge.CallChannel

internal class JniCallChannel(
  private val quickJs: QuickJs,
  private val instance: Long
) : CallChannel {
  override fun serviceNamesArray() = serviceNamesArray(quickJs.context, instance)

  private external fun serviceNamesArray(
    context: Long,
    instance: Long
  ): Array<String>

  override fun invoke(
    instanceName: String,
    funName: String,
    encodedArguments: Array<String>
  ) = invoke(quickJs.context, instance, instanceName, funName, encodedArguments)

  private external fun invoke(
    context: Long,
    instance: Long,
    instanceName: String,
    funName: String,
    encodedArguments: Array<String>,
  ): Array<String>

  override fun invokeSuspending(
    instanceName: String,
    funName: String,
    encodedArguments: Array<String>,
    callbackName: String
  ) = invokeSuspending(
    quickJs.context, instance, instanceName, funName, encodedArguments, callbackName
  )

  private external fun invokeSuspending(
    context: Long,
    instance: Long,
    instanceName: String,
    funName: String,
    encodedArguments: Array<String>,
    callbackName: String,
  )

  override fun disconnect(instanceName: String): Boolean =
    disconnect(quickJs.context, instance, instanceName)

  private external fun disconnect(
    context: Long,
    instance: Long,
    instanceName: String
  ): Boolean
}
