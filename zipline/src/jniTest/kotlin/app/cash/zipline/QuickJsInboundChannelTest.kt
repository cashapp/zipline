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

import app.cash.zipline.internal.bridge.inboundChannelName
import com.google.common.truth.Truth.assertThat
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
class QuickJsInboundChannelTest {
  private val quickJs = QuickJs.create()

  @After fun tearDown() {
    quickJs.close()
  }

  @Before
  fun setUp() {
    quickJs.evaluate("""
      globalThis.$inboundChannelName = {};
      globalThis.$inboundChannelName.serviceNamesArray = function() {
      };
      globalThis.$inboundChannelName.invoke = function(instanceName, funName, encodedArguments) {
      };
      globalThis.$inboundChannelName.invokeSuspending = function(
        instanceName, funName, encodedArguments, callbackName
      ) {
      };
      globalThis.$inboundChannelName.disconnect = function(instanceName) {
      };
    """.trimIndent())

  }

  @Test
  fun invokeHappyPath() {
    quickJs.evaluate("""
      globalThis.$inboundChannelName.invoke = function(instanceName, funName, encodedArguments) {
        var result = [
          'received call to invoke()',
          instanceName,
          funName
        ];
        result.push(...encodedArguments);
        result.push('and the call was successful!');
        return result;
      };
    """.trimIndent())

    val inboundChannel = quickJs.getInboundChannel()
    val result = inboundChannel.invoke(
      instanceName = "theInstanceName",
      funName = "theFunName",
      encodedArguments = arrayOf("firstArg", "secondArg"),
    )
    assertThat(result).asList().containsExactly(
      "received call to invoke()",
      "theInstanceName",
      "theFunName",
      "firstArg",
      "secondArg",
      "and the call was successful!",
    ).inOrder()
  }

  @Test
  fun invokeSuspendingHappyPath() {
    quickJs.evaluate("""
      var callLog = [];
      globalThis.$inboundChannelName.invoke = function(instanceName, funName, encodedArguments) {
        return callLog.pop();
      };
      globalThis.$inboundChannelName.invokeSuspending = function(
        instanceName, funName, encodedArguments, callbackName
      ) {
        var call = [
          'received call to invokeSuspending()',
          instanceName,
          funName,
          callbackName
        ];
        call.push(...encodedArguments);
        call.push('and the call was successful!');
        callLog.push(call);
      };
    """.trimIndent())

    val inboundChannel = quickJs.getInboundChannel()
    inboundChannel.invokeSuspending(
      instanceName = "theInstanceName",
      funName = "theFunName",
      encodedArguments = arrayOf("firstArg", "secondArg"),
      callbackName = "theCallbackName",
    )
    val result = inboundChannel.invoke("", "", arrayOf())
    assertThat(result).asList().containsExactly(
      "received call to invokeSuspending()",
      "theInstanceName",
      "theFunName",
      "theCallbackName",
      "firstArg",
      "secondArg",
      "and the call was successful!",
    ).inOrder()
  }

  @Test
  fun serviceNamesArrayHappyPath() {
    quickJs.evaluate("""
      var callLog = [];
      globalThis.$inboundChannelName.serviceNamesArray = function() {
        return ['service one', 'service two'];
      };
    """.trimIndent())

    val inboundChannel = quickJs.getInboundChannel()
    val result = inboundChannel.serviceNamesArray()
    assertThat(result).asList().containsExactly(
      "service one",
      "service two"
    ).inOrder()
  }

  @Test
  fun disconnectHappyPath() {
    quickJs.evaluate("""
      var callLog = [];
      globalThis.$inboundChannelName.invoke = function(instanceName, funName, encodedArguments) {
        return callLog.pop();
      };
      globalThis.$inboundChannelName.disconnect = function(instanceName) {
        callLog.push(['disconnect', instanceName]);
        return true;
      };
    """.trimIndent())

    val inboundChannel = quickJs.getInboundChannel()
    assertThat(inboundChannel.disconnect("service one")).isTrue()
    val result = inboundChannel.invoke("", "", arrayOf())
    assertThat(result).asList().containsExactly(
      "disconnect",
      "service one"
    ).inOrder()
  }

  @Test
  fun noInboundChannelThrows() {
    quickJs.evaluate("""
      delete globalThis.$inboundChannelName;
    """.trimIndent())

    val t = assertFailsWith<IllegalArgumentException> {
      quickJs.getInboundChannel()
    }
    assertThat(t)
      .hasMessageThat()
      .isEqualTo("A global JavaScript object called $inboundChannelName was not found")
  }

  @Test
  fun callFunctionAfterClosingQuickJsThrows() {
    val inboundChannel = quickJs.getInboundChannel()
    quickJs.close()

    val t = assertFailsWith<NullPointerException> {
      inboundChannel.disconnect("service one")
    }
    assertThat(t)
      .hasMessageThat()
      .isEqualTo("Null QuickJs context - did you close your QuickJs?")
  }

  /**
   * We expect most failures to be caught and encoded by kotlinx.serialization in Zipline. But if
   * that crashes it could throw a JavaScript exception. Confirm such exceptions are reasonable.
   */
  @Test fun inboundChannelThrowsInJavaScript() {
    quickJs.evaluate("""
      function goBoom() {
        noSuchMethod();
      }
      globalThis.$inboundChannelName.disconnect = function(instanceName) {
        goBoom();
      };
    """.trimIndent(), "explode.js")
    val inboundChannel = quickJs.getInboundChannel()
    val t = assertFailsWith<QuickJsException> {
      inboundChannel.disconnect("service one")
    }
    assertThat(t)
      .hasMessageThat()
      .isEqualTo("'noSuchMethod' is not defined")
    assertThat(t.stackTraceToString()).contains("JavaScript.goBoom(explode.js:2)")
  }
}
