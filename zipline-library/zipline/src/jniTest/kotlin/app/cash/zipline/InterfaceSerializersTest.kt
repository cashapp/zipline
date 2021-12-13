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

import app.cash.zipline.testing.MessageInterface
import app.cash.zipline.testing.MessageInterfaceSerializersModule
import app.cash.zipline.testing.RealMessageInterface
import app.cash.zipline.testing.RequestInterfaceService
import app.cash.zipline.testing.ResponseInterfaceService
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * When requests and responses are interfaces we don't get crashes early; we get them late. This is
 * a weakness in kotlinx.serialization, which is lazy when resolving serializers for interfaces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InterfaceSerializersTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher, MessageInterfaceSerializersModule)
  private val ziplineNoSerializer = Zipline.create(dispatcher)

  @Before fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
    ziplineNoSerializer.loadTestingJs()
  }

  @After fun tearDown(): Unit = runBlocking(dispatcher) {
    zipline.close()
    ziplineNoSerializer.close()
  }

  @Test fun jvmToJsRequestInterfaceSucceeds(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate(
      "testing.app.cash.zipline.testing.prepareInterfaceSerializersJsBridges()"
    )
    val service = zipline.get<RequestInterfaceService>("requestInterfaceService")
    assertThat(service.echo(RealMessageInterface("Andrew")))
      .isEqualTo("JS received an interface, Andrew")
  }

  @Test fun jvmToJsResponseInterfaceSucceeds(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate(
      "testing.app.cash.zipline.testing.prepareInterfaceSerializersJsBridges()"
    )
    val service = zipline.get<ResponseInterfaceService>("responseInterfaceService")
    assertThat(service.echo("Andrew"))
      .isEqualTo(RealMessageInterface("JS returned an interface, Andrew"))
  }

  @Test fun jsToJvmRequestInterfaceSucceeds(): Unit = runBlocking(dispatcher) {
    zipline.set<RequestInterfaceService>(
      "requestInterfaceService",
      JvmMessageInterfaceService()
    )

    val result = zipline.quickJs.evaluate(
      "testing.app.cash.zipline.testing.callInterfaceRequest()"
    )
    assertThat(result).isEqualTo("JVM received an interface, Jesse")
  }

  @Test fun jsToJvmResponseInterfaceSucceeds(): Unit = runBlocking(dispatcher) {
    zipline.set<ResponseInterfaceService>(
      "responseInterfaceService",
      JvmMessageInterfaceService()
    )

    val result = zipline.quickJs.evaluate(
      "testing.app.cash.zipline.testing.callInterfaceResponse()"
    )
    assertThat(result).isEqualTo("JVM returned an interface, Jesse")
  }

  @Test fun jsToJvmInterfaceRequestFailsLate(): Unit = runBlocking(dispatcher) {
    ziplineNoSerializer.set<RequestInterfaceService>(
      "requestInterfaceService",
      JvmMessageInterfaceService()
    )

    val exception = assertFailsWith<QuickJsException> {
      ziplineNoSerializer.quickJs.evaluate(
        "testing.app.cash.zipline.testing.callInterfaceRequest()"
      )
    }
    assertThat(exception).hasMessageThat()
      .contains("Class 'RealMessageInterface' is not registered")
  }

  @Test fun jsToJvmInterfaceResponseFailsLate(): Unit = runBlocking(dispatcher) {
    ziplineNoSerializer.set<ResponseInterfaceService>(
      "responseInterfaceService",
      JvmMessageInterfaceService()
    )

    val exception = assertFailsWith<QuickJsException> {
      ziplineNoSerializer.quickJs.evaluate(
        "testing.app.cash.zipline.testing.callInterfaceResponse()"
      )
    }
    assertThat(exception).hasMessageThat()
      .contains("Class 'RealMessageInterface' is not registered")
  }

  private class JvmMessageInterfaceService : RequestInterfaceService, ResponseInterfaceService {
    override fun echo(request: MessageInterface) =
      "JVM received an interface, ${request.message}"

    override fun echo(request: String) =
      RealMessageInterface("JVM returned an interface, $request")
  }
}
