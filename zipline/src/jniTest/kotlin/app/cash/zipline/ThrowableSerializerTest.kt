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

import app.cash.zipline.internal.bridge.ThrowableSerializer
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.util.Locale.US
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.junit.Test

class ThrowableSerializerTest {
  private val json = Json {
    prettyPrint = true
    serializersModule = SerializersModule {
      contextual(Throwable::class, ThrowableSerializer)
    }
  }
  private val newline: String
  private val newlineEscape: String
  init {
    if ("windows" in System.getProperty("os.name").lowercase(US)) {
      newline = "\r\n"
      newlineEscape = "\\r\\n"
    } else {
      newline = "\n"
      newlineEscape = "\\n"
    }
  }

  @Test fun happyPath() {
    val exception = Exception("boom")
    exception.stackTrace = arrayOf(
      StackTraceElement("ThrowableSerializerTest", "goBoom", "test.kt", 5),
    )

    val exceptionJson = """
      |{
      |    "types": [],
      |    "stacktraceString": "java.lang.Exception: boom$newlineEscape\tat ThrowableSerializerTest.goBoom(test.kt:5)"
      |}
      """.trimMargin()

    assertEquals(
      exceptionJson,
      json.encodeToString(ThrowableSerializer, exception),
    )

    val decoded = json.decodeFromString(ThrowableSerializer, exceptionJson)
    assertEquals(ZiplineException::class, decoded::class)
    assertEquals(
      "java.lang.Exception: boom$newline\tat ThrowableSerializerTest.goBoom(test.kt:5)",
      decoded.message,
    )
  }

  @Test fun unrecognizedExceptionTypeDecodesAsException() {
    val exceptionJson = """
      |{
      |    "types": [
      |        "SomeFutureException",
      |        "AnotherFutureException"
      |    ],
      |    "stacktraceString": "java.lang.Exception: boom"
      |}
      """.trimMargin()

    val decoded = json.decodeFromString(ThrowableSerializer, exceptionJson)
    assertEquals(ZiplineException::class, decoded::class)
  }

  @Test fun ziplineApiMismatchExceptionStripsPrefix() {
    val exceptionJson = """
      |{
      |    "types": [
      |        "ZiplineApiMismatchException"
      |    ],
      |    "stacktraceString": "ZiplineApiMismatchException: boom"
      |}
      """.trimMargin()

    val decoded = json.decodeFromString(ThrowableSerializer, exceptionJson)
    assertEquals(ZiplineApiMismatchException::class, decoded::class)
    assertEquals("boom", decoded.message)
  }

  @Test fun ziplineApiMismatchExceptionUnknownSubtypeDoesNotStripPrefix() {
    val exceptionJson = """
      |{
      |    "types": [
      |        "SomeFutureException",
      |        "ZiplineApiMismatchException"
      |    ],
      |    "stacktraceString": "SomeFutureException: boom"
      |}
      """.trimMargin()

    val decoded = json.decodeFromString(ThrowableSerializer, exceptionJson)
    assertEquals(ZiplineApiMismatchException::class, decoded::class)
    assertEquals("SomeFutureException: boom", decoded.message)
  }

  @Test fun cancellationExceptionReturnsConstantInstance() {
    val exception = CancellationException("boom")
    exception.stackTrace = arrayOf(
      StackTraceElement("ThrowableSerializerTest", "goBoom", "test.kt", 5),
    )

    val exceptionJson = """
      |{
      |    "types": [
      |        "CancellationException"
      |    ],
      |    "stacktraceString": ""
      |}
      """.trimMargin()

    assertThat(json.encodeToString(ThrowableSerializer, exception)).isEqualTo(exceptionJson)

    val decoded = json.decodeFromString(ThrowableSerializer, exceptionJson)
    assertEquals(CancellationException::class, decoded::class)
    assertThat(decoded.message).isEqualTo("canceled")
  }
}
