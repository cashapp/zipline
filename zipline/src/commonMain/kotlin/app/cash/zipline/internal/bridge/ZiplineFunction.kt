/*
 * Copyright (C) 2022 Block, Inc.
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

import app.cash.zipline.ZiplineService
import kotlinx.serialization.KSerializer

@PublishedApi
internal abstract class ZiplineFunction<T : ZiplineService>(
  val name: String,
  argSerializers: List<KSerializer<*>>,

  /**
   * For blocking calls this is a serializer for the standalone result. For suspending calls this
   * is a serializer for a `SuspendCallback<T>` where `T` is the response type.
   */
  val resultOrSuspendCallbackSerializer: KSerializer<*>,
) {
  val argsListSerializer = ArgsListSerializer(argSerializers)

  /** A serializer for a `Result<T>` which supports success or failure. */
  val kotlinResultSerializer = ResultSerializer(resultOrSuspendCallbackSerializer)

  val isClose
    get() = name == "fun close(): kotlin.Unit"

  open fun call(service: T, args: List<*>): Any? {
    error("unexpected call")
  }

  open suspend fun callSuspending(service: T, args: List<*>): Any? {
    error("unexpected call")
  }
}
