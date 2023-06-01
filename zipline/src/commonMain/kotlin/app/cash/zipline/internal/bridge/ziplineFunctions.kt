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

import app.cash.zipline.ZiplineFunction
import app.cash.zipline.ZiplineService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@PublishedApi
internal abstract class ReturningZiplineFunction<T : ZiplineService>(
  override val id: String,
  override val signature: String,
  argSerializers: List<KSerializer<*>>,
  resultSerializer: KSerializer<*>,
) : ZiplineFunction<T> {
  val argsListSerializer = ArgsListSerializer(argSerializers)

  /** A serializer for a `kotlin.Result<T>` which supports success or failure. */
  val resultSerializer = ResultOrCallbackSerializer(resultSerializer)

  override val isSuspending get() = false

  override val isClose
    get() = signature == "fun close(): kotlin.Unit"

  abstract fun call(service: T, args: List<*>): Any?

  override fun toString() = signature
}

@PublishedApi
internal abstract class SuspendingZiplineFunction<T : ZiplineService>(
  override val id: String,
  override val signature: String,
  argSerializers: List<KSerializer<*>>,
  resultSerializer: KSerializer<*>,

  /** A serializer for a `SuspendCallback<R>` where `R` is the response type. */
  val suspendCallbackSerializer: KSerializer<*>,
) : ZiplineFunction<T> {
  val argsListSerializer = ArgsListSerializer(argSerializers)

  val resultOrCallbackSerializer = ResultOrCallbackSerializer(resultSerializer)

  override val isSuspending get() = true
  override val isClose get() = false

  abstract suspend fun callSuspending(service: T, args: List<*>): Any?

  override fun toString() = signature
}

@Serializable
internal class SerializableZiplineFunction(
  override val id: String,
  override val signature: String,
  override val isSuspending: Boolean,
) : ZiplineFunction<ZiplineService> {
  constructor(
    function: ZiplineFunction<*>,
  ) : this(function.id, function.signature, function.isSuspending)

  override val isClose
    get() = signature == "fun close(): kotlin.Unit"
}
