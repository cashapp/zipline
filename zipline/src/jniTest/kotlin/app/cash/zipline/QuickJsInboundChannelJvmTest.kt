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

import app.cash.zipline.internal.bridge.INBOUND_CHANNEL_NAME
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Confirm connectivity from Kotlin to a JavaScript implementation of CallChannel.
 *
 * This low-level test uses raw JavaScript to test connectivity. In practice this interface will
 * only ever be used by Kotlin/JS.
 */
class QuickJsInboundChannelJvmTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Before
  fun setUp() {
    quickJs.evaluate(
      """
      globalThis.$INBOUND_CHANNEL_NAME = {};
      globalThis.$INBOUND_CHANNEL_NAME.call = function(instanceName, funName, encodedArguments) {
      };
      globalThis.$INBOUND_CHANNEL_NAME.disconnect = function(instanceName) {
      };
    """.trimIndent(),
    )
  }

  /**
   * We expect most failures to be caught and encoded by kotlinx.serialization in Zipline. But if
   * that crashes it could throw a JavaScript exception. Confirm such exceptions are reasonable.
   */
  @Test fun inboundChannelThrowsInJavaScript() {
    quickJs.evaluate(
      """
      function goBoom() {
        noSuchMethod();
      }
      globalThis.$INBOUND_CHANNEL_NAME.disconnect = function(instanceName) {
        goBoom();
      };
    """.trimIndent(),
      "explode.js",
    )
    val inboundChannel = quickJs.getInboundChannel()
    val t = assertFailsWith<QuickJsException> {
      inboundChannel.disconnect("service one")
    }
    assertThat(t.message!!)
      .isEqualTo("'noSuchMethod' is not defined")
    assertThat(t.stackTraceToString()).contains("JavaScript.goBoom(explode.js:2)")
  }
}
