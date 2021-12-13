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

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingDeque
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher)

  private val logRecords = LinkedBlockingDeque<LogRecord>()
  private val logHandler = object : Handler() {
    override fun publish(record: LogRecord) {
      logRecords += record
    }

    override fun flush() {
    }

    override fun close() {
    }
  }.apply {
    level = Level.FINEST
  }

  @Before
  fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
    Logger.getLogger(Zipline::class.qualifiedName).apply {
      level = Level.FINEST
      addHandler(logHandler)
    }
  }

  @After fun tearDown(): Unit = runBlocking(dispatcher) {
    Logger.getLogger(Zipline::class.qualifiedName).removeHandler(logHandler)
    zipline.close()
  }

  @Test fun logAllLevels(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.consoleLogAllLevels()")
    assertEquals("INFO: 1. this is message 1 of 5. Its level is 'info'.", takeLogMessage())
    assertEquals("INFO: 2. this message has level 'log'.", takeLogMessage())
    assertEquals("WARNING: 3. this message has level 'warn'.", takeLogMessage())
    assertEquals("SEVERE: 4. this message has level 'error'.", takeLogMessage())
    assertEquals("INFO: 5. this is the last message", takeLogMessage())
    assertNull(takeLogMessage())
  }

  @Test fun logWithThrowable(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.consoleLogWithThrowable()")

    val record1 = logRecords.take()
    assertThat(record1.level).isEqualTo(Level.SEVERE)
    assertThat(record1.message).isEqualTo("1. something went wrong")
    assertThat(record1.thrown.stackTraceToString()).matches(
      """(?s).*IllegalStateException: boom!""" +
        """.*at goBoom1""" +
        """.*at goBoom2""" +
        """.*at goBoom3""" +
        """.*at consoleLogWithThrowable""" +
        """.*"""
    )

    val record2 = logRecords.take()
    assertThat(record2.message).isEqualTo("")
    assertThat(record2.thrown.stackTraceToString()).contains("2. exception only")

    val record3 = logRecords.take()
    assertThat(record3.message)
      .isEqualTo("3. multiple exceptions IllegalStateException: number two!")
    assertThat(record3.thrown.stackTraceToString()).contains("IllegalStateException: number one!")

    val record4 = logRecords.take()
    assertThat(record4.message)
      .isEqualTo("4. message second")
    assertThat(record4.thrown.stackTraceToString())
      .contains("IllegalStateException: exception first!")

    val record5 = logRecords.take()
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
  @Test fun logWithArguments(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.consoleLogWithArguments()")
    assertEquals("INFO: this message for %s is a %d out of %d Jesse 8 10", takeLogMessage())
    assertNull(takeLogMessage())
  }

  private fun takeLogMessage(): String? {
    val record = logRecords.poll() ?: return null
    return "${record.level}: ${record.message}"
  }
}
