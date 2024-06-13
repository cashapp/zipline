/*
 * Copyright (C) 2024 Cash App
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

import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.newEndpointPair
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Test [ZiplineFunction.asDynamicFunction] and  [ZiplineFunction.asDynamicSuspendingFunction]. This
 * test only works on Kotlin/JS because those functions only exist there.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class DynamicFunctionTest {

  @Test
  fun happyPath() = runTest {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : EchoService {
      override fun echo(request: EchoRequest) = EchoResponse("yo, ${request.message}")
    }

    endpointA.bind<EchoService>("helloService", service)
    val client = endpointB.take<EchoService>("helloService")

    val echo = client.sourceType!!.functions.first { "echo" in it.signature }
    val dynamicEcho = echo.asDynamicFunction()

    val request = js("""{message:"dynamic caller!"}""")
    val response = dynamicEcho(client, arrayOf(request))
    assertThat(JSON.stringify(response))
      .isEqualTo("""{"message":"yo, dynamic caller!"}""")
  }

  @Test
  fun exceptions() = runTest {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : EchoService {
      override fun echo(request: EchoRequest) = throw Exception("boom!")
    }

    endpointA.bind<EchoService>("service", service)
    val client = endpointB.take<EchoService>("service")

    val echo = client.sourceType!!.functions.first { "echo" in it.signature }
    val dynamicEcho = echo.asDynamicFunction()

    val request = js("""{message:"will it throw?"}""")
    val exception = assertFailsWith<ZiplineException> {
      dynamicEcho(client, arrayOf(request))
    }
    assertThat(exception.message).isNotNull().contains("boom!")
  }

  /** This test exercises the `SuspendCallback` path for suspending calls. */
  @Test
  fun suspendedFunctionSuspends() = runTest {
    val (endpointA, endpointB) = newEndpointPair(this)

    val requests = Channel<EchoRequest>(1)
    val responses = Channel<EchoResponse>(1)

    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        requests.send(request)
        return responses.receive()
      }
    }

    endpointA.bind<SuspendingEchoService>("service", service)
    val client = endpointB.take<SuspendingEchoService>("service")

    val echo = client.sourceType!!.functions.first { "suspendingEcho" in it.signature }
    val dynamicEcho = echo.asDynamicSuspendingFunction()

    val deferred: Deferred<Any?> = async {
      val request = js("""{message:"suspend this call!"}""")
      return@async dynamicEcho(client, arrayOf(request))
    }

    assertThat(requests.receive()).isEqualTo(EchoRequest("suspend this call!"))
    responses.send(EchoResponse("yo, suspended caller"))
    assertThat(JSON.stringify(deferred.await()))
      .isEqualTo("""{"message":"yo, suspended caller"}""")
  }

  /** This test exercises the synchronous return case for suspending calls. */
  @Test
  fun suspendedFunctionNotSuspended() = runTest {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        assertThat(request).isEqualTo(EchoRequest("don't suspend this call!"))
        return EchoResponse("yo, unsuspended caller")
      }
    }

    endpointA.bind<SuspendingEchoService>("service", service)
    val client = endpointB.take<SuspendingEchoService>("service")

    val echo = client.sourceType!!.functions.first { "suspendingEcho" in it.signature }
    val dynamicEcho = echo.asDynamicSuspendingFunction()

    val request = js("""{message:"don't suspend this call!"}""")
    val response = dynamicEcho(client, arrayOf(request))

    assertThat(JSON.stringify(response))
      .isEqualTo("""{"message":"yo, unsuspended caller"}""")
  }

  @Test
  fun unstructuredValues() = runTest {
    acceptAndProduce(
      kotlinValue = "hello",
      jsValue = "hello",
    )
    acceptAndProduce<Int>(
      kotlinValue = 5,
      jsValue = 5,
    )
    acceptAndProduce<Double>(
      kotlinValue = 2147483648.0,
      jsValue = 2147483648.0,
    )
    acceptAndProduce<Long>(
      kotlinValue = 2147483648L,
      jsValue = js("2147483648"),
    )
    acceptAndProduce<Long>(
      kotlinValue = 9007199254740991L,
      jsValue = js("9007199254740991"),
    )
    acceptAndProduce<Boolean>(
      kotlinValue = true,
      jsValue = true,
    )
    acceptAndProduce<String?>(
      kotlinValue = null,
      jsValue = null,
    )
  }

  @Test
  fun collections() = runTest {
    acceptAndProduce<List<String>>(
      kotlinValue = listOf(element = "hello"),
      jsValue = js("""["hello"]"""),
    )
    acceptAndProduce<List<String?>>(
      kotlinValue = listOf("hello", null),
      jsValue = js("""["hello", null]"""),
    )
  }

  @Test
  fun objects() = runTest {
    acceptAndProduce<EchoRequest>(
      kotlinValue = EchoRequest(message = "hello"),
      jsValue = js("""{"message":"hello"}"""),
    )
    acceptAndProduce<EchoRequest?>(
      kotlinValue = null,
      jsValue = null,
    )
  }

  /** Large longs can't be serialized due to a potential precision loss. */
  @Test @Ignore
  fun encodeDecodeLargeLong() = runTest {
    acceptAndProduce<Long>(
      kotlinValue = 9007199254740992L,
      jsValue = 9007199254740992,
    )
  }

  /**
   * This is broken in Kotlin Serialization.
   * https://github.com/Kotlin/kotlinx.serialization/issues/2713
   */
  @Test @Ignore
  fun encodeDecodeUint() = runTest {
    acceptAndProduce<UInt>(
      kotlinValue = 0xFF0BB634u,
      jsValue = 4278957620.0,
    )
  }

  private suspend inline fun <reified T> CoroutineScope.acceptAndProduce(
    kotlinValue: T,
    jsValue: dynamic = kotlinValue,
  ) {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : AcceptProduceService<T> {
      override fun blockingAccept(argument: T) {
        assertThat(argument).isEqualTo(kotlinValue)
      }

      override suspend fun suspendingAccept(argument: T) {
        assertThat(argument).isEqualTo(kotlinValue)
      }

      override fun blockingProduce(): T {
        return kotlinValue
      }

      override suspend fun suspendingProduce(): T {
        return kotlinValue
      }
    }

    endpointA.bind<AcceptProduceService<T>>("service", service)
    val client = endpointB.take<AcceptProduceService<T>>("service")
    val functions = client.sourceType!!.functions

    val blockingAcceptFunction = functions.first { "blockingAccept" in it.signature }
      .asDynamicFunction()
    val blockingAcceptResult = blockingAcceptFunction(client, arrayOf(jsValue))
    assertThat(JSON.stringify(blockingAcceptResult)).isEqualTo("{}")

    val suspendingAcceptFunction = functions.first { "suspendingAccept" in it.signature }
      .asDynamicSuspendingFunction()
    val suspendingAcceptResult = suspendingAcceptFunction(client, arrayOf(jsValue))
    assertThat(JSON.stringify(suspendingAcceptResult)).isEqualTo("{}")

    val blockingProduceFunction = functions.first { "blockingProduce" in it.signature }
      .asDynamicFunction()
    val blockingProduceResult = blockingProduceFunction(client, arrayOf<Any?>())
    assertThat(JSON.stringify(blockingProduceResult)).isEqualTo(JSON.stringify(jsValue))

    val suspendingProduceFunction = functions.first { "suspendingProduce" in it.signature }
      .asDynamicSuspendingFunction()
    val suspendingProduceResult = suspendingProduceFunction(client, arrayOf<Any?>())
    assertThat(JSON.stringify(suspendingProduceResult)).isEqualTo(JSON.stringify(jsValue))
  }

  interface AcceptProduceService<T> : ZiplineService {
    fun blockingAccept(argument: T)
    suspend fun suspendingAccept(argument: T)
    fun blockingProduce(): T
    suspend fun suspendingProduce(): T
  }

  @Test
  fun multipleArguments() = runTest {
    val (endpointA, endpointB) = newEndpointPair(this)

    val service = object : ConcatService {
      override fun blockingConcat(a: String, b: Int, c: String) = "$a$b$c"
      override suspend fun suspendingConcat(a: String, b: Int, c: String) = "$a$b$c"
    }

    endpointA.bind<ConcatService>("service", service)
    val client = endpointB.take<ConcatService>("service")
    val functions = client.sourceType!!.functions

    val blockingConcat = functions.first { "blockingConcat" in it.signature }
    val dynamicBlockingConcat = blockingConcat.asDynamicFunction()
    assertThat(JSON.stringify(dynamicBlockingConcat(client, arrayOf("one ", 2, " three"))))
      .isEqualTo(""""one 2 three"""")

    val suspendingConcat = functions.first { "suspendingConcat" in it.signature }
    val dynamicSuspendingConcat = suspendingConcat.asDynamicSuspendingFunction()
    assertThat(JSON.stringify(dynamicSuspendingConcat(client, arrayOf("four ", 5, " six"))))
      .isEqualTo(""""four 5 six"""")
  }

  interface ConcatService : ZiplineService {
    fun blockingConcat(a: String, b: Int, c: String): String
    suspend fun suspendingConcat(a: String, b: Int, c: String): String
  }
}
