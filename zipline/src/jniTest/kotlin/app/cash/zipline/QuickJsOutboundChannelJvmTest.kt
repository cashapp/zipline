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

import app.cash.zipline.internal.bridge.OUTBOUND_CHANNEL_NAME
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Confirm connectivity from JavaScript to a Kotlin implementation of CallChannel.
 *
 * This low-level test uses raw JavaScript to test connectivity. In practice this interface will
 * only ever be used by Kotlin/JS.
 */
class QuickJsOutboundChannelJvmTest {
  private val quickJs = QuickJs.create()
  private val callChannel = LoggingCallChannel()

  @Before
  fun setUp() {
    quickJs.initOutboundChannel(callChannel)
  }

  @After fun tearDown() {
    quickJs.close()
  }

  @Test
  fun jvmExceptionsWithUnifiedStackTrace() {
    callChannel.disconnectThrow = true
    val t = assertFailsWith<UnsupportedOperationException> {
      quickJs.evaluate(
        """
        function f1() {
          globalThis.$OUTBOUND_CHANNEL_NAME.disconnect('theInstanceName');
        }

        function f2() {
          f1();
        }

        f2();
      """.trimIndent(),
        "explode.js",
      )
    }
    assertThat(t.message!!)
      .isEqualTo("boom!")
    val stackTrace = t.stackTraceToString()

    // The stack trace starts with the throwing code.
    assertThat(stackTrace).contains("LoggingCallChannel.disconnect")

    // It includes JavaScript line numbers.
    assertThat(stackTrace).contains("JavaScript.disconnect(native)")
    assertThat(stackTrace).contains("JavaScript.f1(explode.js:2)")
    assertThat(stackTrace).contains("JavaScript.f2(explode.js:6)")

    // It includes JNI bridging into JavaScript.
    assertThat(stackTrace).contains("QuickJs.execute(Native Method)")

    // And this test method.
    assertThat(stackTrace).contains("QuickJsOutboundChannelJvmTest.jvmExceptionsWithUnifiedStackTrace")
  }
}
