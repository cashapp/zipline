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
package app.cash.zipline.internal.bridge

import kotlin.js.JsName

@PublishedApi
internal interface InternalBridge {
  /**
   * Internal function used to bridge method calls from Java or Android to JavaScript.
   *
   * The structure of [encodedArguments] is the following:
   *
   *  * 1 int (4 bytes): the number of parameters
   *  * For each parameter:
   *    * 1 int (4 bytes): the number of bytes in the parameter value. If the parameter value is
   *      null this is [BYTE_COUNT_NULL] and no bytes follow.
   *    * the bytes of the parameter value
   *
   * The structure of the result is the following:
   *
   *  * 1 byte: the result type. Either [RESULT_TYPE_NORMAL] or [RESULT_TYPE_NORMAL]
   *  * 1 int (4 bytes): the number of bytes in the result value. If the result is null this is
   *    [BYTE_COUNT_NULL] and no bytes follow.
   *  * the bytes of the result value
   */
  @JsName("invoke")
  fun invoke(instanceName: String, funName: String, encodedArguments: ByteArray): ByteArray

  /** Like [invoke], but the respose is delivered to the [SuspendCallback] named [callbackName]. */
  @JsName("invokeSuspending")
  fun invokeSuspending(
    instanceName: String,
    funName: String,
    encodedArguments: ByteArray,
    callbackName: String
  )
}

internal const val BYTE_COUNT_NULL = -1
internal const val RESULT_TYPE_NORMAL = 0 as Byte
internal const val RESULT_TYPE_EXCEPTION = 1 as Byte
