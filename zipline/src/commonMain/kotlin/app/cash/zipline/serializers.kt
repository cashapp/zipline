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
package app.cash.zipline

import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer

/**
 * Returns a [KSerializer] for [T] that performs pass-by-reference instead of pass-by-value. This is
 * only necessary when a service is passed as a member of another serializable type; Zipline
 * automatically does pass-by-reference for service parameters and return values.
 *
 * To use this, first register the serializer for your service in a
 * [kotlinx.serialization.modules.SerializersModule].
 *
 * ```kotlin
 * val mySerializersModule = SerializersModule {
 *   contextual(EchoService::class, ziplineServiceSerializer())
 * }
 * ```
 *
 * Next, use that [kotlinx.serialization.modules.SerializersModule] when you create your Zipline
 * instance, in both the host application and the Kotlin/JS code:
 *
 * ```kotlin
 * val zipline = Zipline.create(dispatcher, mySerializersModule)
 * ```
 *
 * Finally, annotate the service property with [kotlinx.serialization.Contextual]. This instructs
 * Kotlin serialization to use the registered serializer.
 *
 * ```kotlin
 * @Serializable
 * data class ServiceCreatedResult(
 *   val greeting: String,
 *   @Contextual val service: EchoService,
 * )
 * ```
 *
 * The caller must call [ZiplineService.close] when they are done with the returned service to
 * release the held reference.
 */
fun <T : ZiplineService> ziplineServiceSerializer(): KSerializer<T> {
  error("unexpected call to ziplineServiceSerializer(): is the Zipline plugin configured?")
}

/**
 * Returns a [KSerializer] for [T] that performs pass-by-reference instead of pass-by-value. Use
 * this when implementing contextual serialization for a parameterized type.
 */
fun <T : ZiplineService> ziplineServiceSerializer(
  kClass: KClass<*>,
  typeArgumentsSerializers: List<KSerializer<*>> = emptyList(),
): KSerializer<T> {
  error("unexpected call to ziplineServiceSerializer(): is the Zipline plugin configured?")
}

@PublishedApi
internal fun <T : ZiplineService> ziplineServiceSerializer(
  ziplineServiceAdapter: ZiplineServiceAdapter<T>,
): KSerializer<T> {
  return ziplineServiceAdapter
}
