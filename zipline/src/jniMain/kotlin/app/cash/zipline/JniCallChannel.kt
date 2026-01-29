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
  private val instance: Long,
) : CallChannel {
  override fun call(callJson: String) = call(quickJs.context, instance, callJson)

  private external fun call(
    context: Long,
    instance: Long,
    callJson: String,
  ): String

  override fun disconnect(instanceName: String): Boolean = disconnect(quickJs.context, instance, instanceName)

  private external fun disconnect(
    context: Long,
    instance: Long,
    instanceName: String,
  ): Boolean
}
