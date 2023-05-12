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

import app.cash.zipline.internal.bridge.FlowSerializer
import app.cash.zipline.internal.bridge.FlowZiplineService
import app.cash.zipline.internal.bridge.ReturningZiplineFunction
import app.cash.zipline.internal.bridge.SuspendCallback
import app.cash.zipline.internal.bridge.SuspendingZiplineFunction
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.kotlinBuiltInSerializersModule
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class NewSerializersTest {
  @Rule @JvmField
  val ziplineTestRule = ZiplineTestRule()
  private val dispatcher = ziplineTestRule.dispatcher
  private val zipline = Zipline.create(dispatcher, kotlinBuiltInSerializersModule)
  private val json = zipline.json

  @Test
  fun stringParameter() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<StringParameter>(),
    ) as ReturningZiplineFunction
    assertEquals(
      String.serializer(),
      function.argsListSerializer.serializers[0],
    )
  }

  interface StringParameter : ZiplineService {
    fun onlyFunction(string: String)
  }

  @Test
  fun stringReturnValue() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<StringReturnValue>(),
    ) as ReturningZiplineFunction
    assertEquals(
      String.serializer(),
      function.resultSerializer.successSerializer,
    )
  }

  interface StringReturnValue : ZiplineService {
    fun onlyFunction(): String
  }

  @Test
  fun stringSuspendResult() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<StringSuspendResult>(),
    ) as SuspendingZiplineFunction<*>
    assertEquals(
      ziplineServiceSerializer<SuspendCallback<String>>(),
      function.suspendCallbackSerializer,
    )
  }

  interface StringSuspendResult : ZiplineService {
    suspend fun onlyFunction(): String
  }

  @Test
  fun byteArrayParameter() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<ByteArrayParameter>(),
    ) as ReturningZiplineFunction
    assertEquals(
      ByteArraySerializer(),
      function.argsListSerializer.serializers[0],
    )
  }

  interface ByteArrayParameter : ZiplineService {
    fun onlyFunction(value: ByteArray)
  }

  @Test
  fun byteArrayReturnValue() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<ByteArrayReturnValue>(),
    ) as ReturningZiplineFunction
    assertEquals(
      ByteArraySerializer(),
      function.resultSerializer.successSerializer,
    )
  }

  interface ByteArrayReturnValue : ZiplineService {
    fun onlyFunction(): ByteArray
  }

  @Test
  fun byteArraySuspendResult() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<ByteArraySuspendResult>(),
    ) as SuspendingZiplineFunction<*>
    assertEquals(
      ziplineServiceSerializer<SuspendCallback<ByteArray>>(),
      function.suspendCallbackSerializer,
    )
  }

  interface ByteArraySuspendResult : ZiplineService {
    suspend fun onlyFunction(): ByteArray
  }

  @Test
  fun genericParameter() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<GenericParameter<String>>(),
    ) as ReturningZiplineFunction
    assertEquals(
      String.serializer(),
      function.argsListSerializer.serializers[0],
    )
  }

  interface GenericParameter<T> : ZiplineService {
    fun onlyFunction(value: T)
  }

  @Test
  fun genericReturnValue() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<GenericReturnValue<String>>(),
    ) as ReturningZiplineFunction
    assertEquals(
      String.serializer(),
      function.resultSerializer.successSerializer,
    )
  }

  interface GenericReturnValue<T> : ZiplineService {
    fun onlyFunction(): T
  }

  @Test
  fun genericSuspendResult() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<GenericSuspendResult<String>>(),
    ) as SuspendingZiplineFunction<*>
    assertEquals(
      ziplineServiceSerializer<SuspendCallback<String>>(),
      function.suspendCallbackSerializer,
    )
  }

  interface GenericSuspendResult<T> : ZiplineService {
    suspend fun onlyFunction(): T
  }

  @Test
  fun genericParameterInList() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<GenericParameterInList<String>>(),
    ) as ReturningZiplineFunction
    assertSerializersEqual(
      ListSerializer(String.serializer()),
      function.argsListSerializer.serializers[0],
      listOf("a", "b", "c"),
    )
  }

  interface GenericParameterInList<T> : ZiplineService {
    fun onlyFunction(value: List<T>)
  }

  @Test
  fun genericReturnValueInList() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<GenericReturnValueInList<String>>(),
    ) as ReturningZiplineFunction
    assertSerializersEqual(
      ListSerializer(String.serializer()),
      function.resultSerializer.successSerializer,
      listOf("a", "b", "c"),
    )
  }

  interface GenericReturnValueInList<T> : ZiplineService {
    fun onlyFunction(): List<T>
  }

  @Ignore("No equals function for these")
  @Test
  fun genericSuspendResultInList() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<GenericSuspendResultInList<String>>(),
    ) as SuspendingZiplineFunction<*>
    assertEquals(
      suspendCallbackSerializer(ListSerializer(String.serializer())),
      function.suspendCallbackSerializer,
    )
  }

  interface GenericSuspendResultInList<T> : ZiplineService {
    suspend fun onlyFunction(): List<T>
  }

  @Test
  fun flowParameter() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<FlowParameter>(),
    ) as ReturningZiplineFunction
    assertEquals(
      FlowSerializer(ziplineServiceSerializer<FlowZiplineService<String>>()),
      function.argsListSerializer.serializers[0],
    )
  }

  interface FlowParameter : ZiplineService {
    fun onlyFunction(value: Flow<String>)
  }

  @Test
  fun flowReturnValue() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<FlowReturnValue>(),
    ) as ReturningZiplineFunction
    assertEquals(
      FlowSerializer(ziplineServiceSerializer<FlowZiplineService<String>>()),
      function.resultSerializer.successSerializer,
    )
  }

  interface FlowReturnValue : ZiplineService {
    fun onlyFunction(): Flow<String>
  }

  @Test
  fun flowSuspendResult() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<FlowSuspendResult>(),
    ) as SuspendingZiplineFunction<*>
    assertEquals(
      suspendCallbackSerializer(flowSerializer(String.serializer())),
      function.suspendCallbackSerializer,
    )
  }

  interface FlowSuspendResult : ZiplineService {
    suspend fun onlyFunction(): Flow<String>
  }

  @Test
  fun genericFlowParameter() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<GenericFlowParameter<String>>(),
    ) as ReturningZiplineFunction
    assertEquals(
      FlowSerializer(ziplineServiceSerializer<FlowZiplineService<String>>()),
      function.argsListSerializer.serializers[0],
    )
  }

  interface GenericFlowParameter<T> : ZiplineService {
    fun onlyFunction(value: Flow<T>)
  }

  @Test
  fun genericFlowReturnValue() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<GenericFlowReturnValue<String>>(),
    ) as ReturningZiplineFunction
    assertEquals(
      FlowSerializer(ziplineServiceSerializer<FlowZiplineService<String>>()),
      function.resultSerializer.successSerializer,
    )
  }

  interface GenericFlowReturnValue<T> : ZiplineService {
    fun onlyFunction(): Flow<T>
  }

  @Test
  fun genericFlowSuspendResult() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<GenericFlowSuspendResult<String>>(),
    ) as SuspendingZiplineFunction<*>
    assertEquals(
      suspendCallbackSerializer(flowSerializer(String.serializer())),
      function.suspendCallbackSerializer,
    )
  }

  interface GenericFlowSuspendResult<T> : ZiplineService {
    suspend fun onlyFunction(): Flow<T>
  }

  @Test
  fun serviceParameter() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<ServiceParameter>(),
    ) as ReturningZiplineFunction
    assertEquals(
      ziplineServiceSerializer<EchoService>(),
      function.argsListSerializer.serializers[0],
    )
  }

  interface ServiceParameter : ZiplineService {
    fun onlyFunction(value: EchoService)
  }

  @Test
  fun serviceReturnValue() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<ServiceReturnValue>(),
    ) as ReturningZiplineFunction
    assertEquals(
      ziplineServiceSerializer<EchoService>(),
      function.resultSerializer.successSerializer,
    )
  }

  interface ServiceReturnValue : ZiplineService {
    fun onlyFunction(): EchoService
  }

  @Test
  fun serviceSuspendResult() = runBlocking(dispatcher) {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<ServiceSuspendResult>(),
    ) as SuspendingZiplineFunction<*>
    assertEquals(
      suspendCallbackSerializer(ziplineServiceSerializer<EchoService>()),
      function.suspendCallbackSerializer,
    )
  }

  interface ServiceSuspendResult : ZiplineService {
    suspend fun onlyFunction(): EchoService
  }

  @Test
  fun contextualServiceParameter() {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<ContextualParameter>(),
      serializersModule = requiresContextualSerializersModule,
    ) as ReturningZiplineFunction<*>
    assertEquals(
      RequiresContextualSerializer,
      function.argsListSerializer.serializers.first(),
    )
  }

  interface ContextualParameter : ZiplineService {
    fun echo(request: @Contextual RequiresContextual)
  }

  @Test
  fun contextualReturnValueService() {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<ContextualReturnValue>(),
      serializersModule = requiresContextualSerializersModule,
    ) as ReturningZiplineFunction<*>
    assertEquals(
      RequiresContextualSerializer,
      function.resultSerializer.successSerializer,
    )
  }

  interface ContextualReturnValue : ZiplineService {
    fun echo(): @Contextual RequiresContextual
  }

  @Test
  fun contextualSuspendingReturnValue() {
    val function = onlyZiplineFunction(
      serviceSerializer = ziplineServiceSerializer<ContextualSuspendingReturnValue>(),
      serializersModule = requiresContextualSerializersModule,
    ) as SuspendingZiplineFunction<*>
    assertEquals(
      suspendCallbackSerializer(RequiresContextualSerializer),
      function.suspendCallbackSerializer,
    )
  }

  interface ContextualSuspendingReturnValue : ZiplineService {
    suspend fun echo(): @Contextual RequiresContextual
  }

  private fun <T> suspendCallbackSerializer(
    resultSerializer: KSerializer<T>,
  ): KSerializer<SuspendCallback<T>> {
    return ziplineServiceSerializer(
      SuspendCallback::class,
      listOf(resultSerializer),
    )
  }

  private fun <T> flowSerializer(elementSerializer: KSerializer<T>): KSerializer<Flow<T>> {
    return FlowSerializer(
      ziplineServiceSerializer(FlowZiplineService::class, listOf(elementSerializer)),
    )
  }

  private fun <T : ZiplineService> onlyZiplineFunction(
    serviceSerializer: KSerializer<T>,
    serializersModule: SerializersModule = json.serializersModule,
  ): ZiplineFunction<T> {
    val serializer = serviceSerializer as ZiplineServiceAdapter<T>
    val functions = serializer.ziplineFunctions(serializersModule)
    return functions.single { !it.isClose }
  }

  private val requiresContextualSerializersModule = SerializersModule {
    contextual(RequiresContextual::class, RequiresContextualSerializer)
  }

  // Note: no @Serializable.
  class RequiresContextual(
    val string: String,
  )

  object RequiresContextualSerializer : KSerializer<RequiresContextual> {
    override val descriptor = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): RequiresContextual {
      return RequiresContextual(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: RequiresContextual) {
      encoder.encodeString(value.string)
    }
  }

  /**
   * Confirm [expected] and [actual] encode and decode the same. This is necessary to check equality
   * on serializers that don't implement [Any.equals].
   */
  private fun assertSerializersEqual(
    expected: KSerializer<*>,
    actual: KSerializer<*>,
    sampleValue: Any?,
  ) {
    assertEquals(actual.descriptor, expected.descriptor)
    assertEquals(
      json.encodeToString(expected as KSerializer<Any?>, sampleValue),
      json.encodeToString(actual as KSerializer<Any?>, sampleValue),
    )
    assertEquals(
      sampleValue,
      json.decodeFromString(actual, json.encodeToString(expected, sampleValue)),
    )
  }
}
