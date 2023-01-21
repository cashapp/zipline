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

import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.ServiceAndPrefix
import app.cash.zipline.testing.ServiceMemberSerializersModule
import app.cash.zipline.testing.ServiceTransformer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Confirm we can pass services as contextual members of serializable types, as described in
 * [ziplineServiceSerializer]. This test confirms it all works across bridges.
 */
class ServiceMemberTest {
  @Rule @JvmField val ziplineTestRule = ZiplineTestRule()
  private val dispatcher = ziplineTestRule.dispatcher
  private val zipline = Zipline.create(dispatcher, ServiceMemberSerializersModule)

  @Before fun setUp() = runBlocking(dispatcher) {
    zipline.loadTestingJs()
  }

  @After fun tearDown() = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test fun serviceMember() = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareServiceMemberJsBridges()")

    val echoService = JvmEchoService()
    val transformer = zipline.take<ServiceTransformer>("serviceTransformer")
    val (prefix, transformed) = transformer.addPrefix(ServiceAndPrefix("OMG! ", echoService))
    assertThat(prefix).isEqualTo("")
    assertThat(transformed.echo(EchoRequest(message = "Jesse")))
      .isEqualTo(EchoResponse("OMG! JVM received 'Jesse'"))
    transformed.close()
  }

  private class JvmEchoService : EchoService {
    override fun echo(request: EchoRequest) = EchoResponse("JVM received '${request.message}'")
  }
}
