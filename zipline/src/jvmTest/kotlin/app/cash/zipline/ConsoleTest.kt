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

import app.cash.zipline.testing.loadTestingJs
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.matches
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ConsoleTest {
  private val dispatcher = StandardTestDispatcher()
  private val zipline = Zipline.create(dispatcher)

  private val logRecords = Channel<LogRecord>(UNLIMITED)
  private val logHandler = object : Handler() {
    override fun publish(record: LogRecord) {
      logRecords.trySend(record)
    }

    override fun flush() {
    }

    override fun close() {
    }
  }.apply {
    level = Level.FINEST
  }

  @Before
  fun setUp(): Unit = runTest(dispatcher) {
    zipline.loadTestingJs()
    Logger.getLogger(Zipline::class.qualifiedName).apply {
      level = Level.FINEST
      addHandler(logHandler)
    }
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.initZipline()")
  }

  @After fun tearDown() = runTest(dispatcher) {
    Logger.getLogger(Zipline::class.qualifiedName).removeHandler(logHandler)
    zipline.close()
  }

  @Test fun logAllLevels() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.consoleLogAllLevels()")

    val record1 = logRecords.receive()
    assertEquals(Level.INFO, record1.level)
    assertEquals("1. this is message 1 of 5. Its level is 'info'.", record1.message)

    val record2 = logRecords.receive()
    assertEquals(Level.INFO, record2.level)
    assertEquals("2. this message has level 'log'.", record2.message)

    val record3 = logRecords.receive()
    assertEquals(Level.WARNING, record3.level)
    assertEquals("3. this message has level 'warn'.", record3.message)

    val record4 = logRecords.receive()
    assertEquals(Level.SEVERE, record4.level)
    assertEquals("4. this message has level 'error'.", record4.message)

    val record5 = logRecords.receive()
    assertEquals(Level.INFO, record5.level)
    assertEquals("5. this is the last message", record5.message)

    assertNull(takeLogMessage())
  }

  @Test fun logWithThrowable() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.consoleLogWithThrowable()")

    val record1 = logRecords.receive()
    assertThat(record1.level).isEqualTo(Level.SEVERE)
    assertThat(record1.message).isEqualTo("1. something went wrong")
    assertThat(record1.thrown.stackTraceToString()).matches(
      Regex(
        """(?s).*IllegalStateException: boom!""" +
          """.*at goBoom1""" +
          """.*at goBoom2""" +
          """.*at goBoom3""" +
          """.*at consoleLogWithThrowable""" +
          """.*""",
      ),
    )

    val record2 = logRecords.receive()
    assertThat(record2.message).isEqualTo("")
    assertThat(record2.thrown.stackTraceToString()).contains("2. exception only")

    val record3 = logRecords.receive()
    assertThat(record3.message)
      .isEqualTo("3. multiple exceptions IllegalStateException: number two!")
    assertThat(record3.thrown.stackTraceToString()).contains("IllegalStateException: number one!")

    val record4 = logRecords.receive()
    assertThat(record4.message)
      .isEqualTo("4. message second")
    assertThat(record4.thrown.stackTraceToString())
      .contains("IllegalStateException: exception first!")

    val record5 = logRecords.receive()
    assertThat(record5.level).isEqualTo(Level.INFO)
    assertThat(record5.message)
      .isEqualTo("5. info with exception")
    assertThat(record4.thrown.stackTraceToString()).contains("IllegalStateException")

    assertNull(takeLogMessage())
  }

  /**
   * Note that this test is checking our expected behavior, but our behavior falls short of what
   * browsers implement. In particular, we don't do string replacement for `%s`, `%d`, etc.
   */
  @Test fun logWithArguments() = runTest(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.consoleLogWithArguments()")

    val record = logRecords.receive()
    assertEquals(Level.INFO, record.level)
    assertEquals("this message for %s is a %d out of %d Jesse 8 10", record.message)

    assertNull(takeLogMessage())
  }

  private fun takeLogMessage(): String? {
    val record = logRecords.tryReceive().getOrNull() ?: return null
    return "${record.level}: ${record.message}"
  }
}
