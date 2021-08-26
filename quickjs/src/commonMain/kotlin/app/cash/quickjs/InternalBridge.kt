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
package app.cash.quickjs

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
   *    * 1 int (4 bytes): the number of bytes in the parameter value
   *    * the bytes of the parameter value
   *
   * The structure of the result is the following:
   *
   *  * 1 int (4 bytes): the number of bytes in the result value
   *  * the bytes of the result value
   */
  @JsName("invokeJs")
  fun invokeJs(instanceName: String, funName: String, encodedArguments: ByteArray): ByteArray
}
