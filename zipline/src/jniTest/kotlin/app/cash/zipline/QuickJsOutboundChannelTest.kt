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

import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.outboundChannelName
import com.google.common.truth.Truth.assertThat
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
class QuickJsOutboundChannelTest {
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
  fun invokeHappyPath() {
    callChannel.invokeResult += "result one"
    callChannel.invokeResult += "result two"
    val invokeResult = quickJs.evaluate("""
      globalThis.${outboundChannelName}.invoke(
        'theInstanceName', 'theFunName', ['firstArg', 'secondArg']
      );
    """.trimIndent()) as Array<Any>
    assertThat(invokeResult).asList().containsExactly(
      "result one",
      "result two",
    )
    assertThat(callChannel.log).containsExactly(
      "invoke(theInstanceName, theFunName, [firstArg, secondArg])",
    )
  }

  @Test
  fun invokeSuspendingHappyPath() {
    quickJs.evaluate("""
      globalThis.${outboundChannelName}.invokeSuspending(
        'theInstanceName', 'theFunName', ['firstArg', 'secondArg'], 'theCallbackName'
      );
    """.trimIndent())
    assertThat(callChannel.log).containsExactly(
      "invokeSuspending(theInstanceName, theFunName, [firstArg, secondArg], theCallbackName)",
    )
  }

  @Test
  fun serviceNamesArrayHappyPath() {
    callChannel.serviceNamesResult += "service one"
    callChannel.serviceNamesResult += "service two"

    val serviceNames = quickJs.evaluate("""
      globalThis.${outboundChannelName}.serviceNamesArray();
    """.trimIndent()) as Array<Any>
    assertThat(serviceNames).asList().containsExactly(
      "service one",
      "service two",
    )
    assertThat(callChannel.log).containsExactly(
      "serviceNamesArray()",
    )
  }

  @Test
  fun disconnectHappyPath() {
    callChannel.disconnectResult = true

    val disconnectResult = quickJs.evaluate("""
      globalThis.${outboundChannelName}.disconnect('theInstanceName');
    """.trimIndent()) as Boolean
    assertThat(disconnectResult).isTrue()
    assertThat(callChannel.log).containsExactly(
      "disconnect(theInstanceName)",
    )
  }

  @Test
  fun jvmExceptionsWithUnifiedStackTrace() {
    callChannel.disconnectThrow = true
    val t = assertFailsWith<UnsupportedOperationException> {
      quickJs.evaluate("""
        function f1() {
          globalThis.${outboundChannelName}.disconnect('theInstanceName');
        }

        function f2() {
          f1();
        }

        f2();
      """.trimIndent(), "explode.js")
    }
    assertThat(t)
      .hasMessageThat()
      .isEqualTo("boom!")
    val stackTrace = t.stackTraceToString()

    // The stack trace starts with the throwing code.
    assertThat(stackTrace).contains("LoggingCallChannel.disconnect")

    // It includes JavaScript line numbers.
    assertThat(stackTrace).contains("JavaScript.disconnect(native)")
    assertThat(stackTrace).contains("JavaScript.f1(explode.js:2)")
    assertThat(stackTrace).contains("JavaScript.f2(explode.js:6)")

    // It includes JNI bridging into JavaScript.
    assertThat(stackTrace).contains("QuickJs.evaluate(Native Method)")

    // And this test method.
    assertThat(stackTrace).contains("QuickJsOutboundChannelTest.jvmExceptionsWithUnifiedStackTrace")
  }

  @Test
  fun lotsOfCalls() {
    val count = quickJs.evaluate(
      """
      |var count = 0;
      |for (var i = 0; i < 100000; i++) {
      |  var result = globalThis.${outboundChannelName}.disconnect('theInstanceName');
      |  if (result) count++;
      |}
      |count;
      """.trimMargin()
    ) as Int
    assertThat(callChannel.log).hasSize(100000)
    assertThat(count).isEqualTo(100000)
  }

  private class LoggingCallChannel : CallChannel {
    val log = mutableListOf<String>()
    val serviceNamesResult = mutableListOf<String>()
    val invokeResult = mutableListOf<String>()
    var disconnectThrow = false
    var disconnectResult = true

    override fun serviceNamesArray(): Array<String> {
      log += "serviceNamesArray()"
      return serviceNamesResult.toTypedArray()
    }

    override fun invoke(
      instanceName: String,
      funName: String,
      encodedArguments: Array<String>
    ): Array<String> {
      log += "invoke($instanceName, $funName, [${encodedArguments.joinToString(", ")}])"
      return invokeResult.toTypedArray()
    }

    override fun invokeSuspending(
      instanceName: String,
      funName: String,
      encodedArguments: Array<String>,
      callbackName: String
    ) {
      log += "invokeSuspending($instanceName, $funName, " +
        "[${encodedArguments.joinToString(", ")}], $callbackName)"
    }

    override fun disconnect(instanceName: String): Boolean {
      log += "disconnect($instanceName)"
      if (disconnectThrow) throw UnsupportedOperationException("boom!")
      return disconnectResult
    }
  }
}
