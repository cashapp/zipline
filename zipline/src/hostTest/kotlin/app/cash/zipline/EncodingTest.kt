/*
 * Copyright (C) 2023 Square, Inc.
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

import app.cash.zipline.testing.EncodingService
import app.cash.zipline.testing.loadTestingJs
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class EncodingTest {
  @OptIn(ExperimentalCoroutinesApi::class)
  private val dispatcher = UnconfinedTestDispatcher()
  private val zipline = Zipline.create(dispatcher)

  @BeforeTest fun setUp() = runBlocking(dispatcher) {
    zipline.loadTestingJs()
  }

  @AfterTest fun tearDown() = runBlocking(dispatcher) {
    zipline.close()
  }

  /**
   * We encode all numbers as doubles for a significant performance gain in `encodeToStringFast()`.
   * Unfortunately this breaks high valued longs (greater than 2^53) because they don't all have an
   * exact representation as a double.
   *
   * We work around this by using `@Contextual` on Longs, plus an encoder that wraps very
   * these high-valued longs in strings.
   */
  @Test fun encodeLongs() = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareEncodingJsBridges()")

    val service = zipline.take<EncodingService>("encodingService")
    assertThat(service.echoLong(Long.MIN_VALUE)).isEqualTo(Long.MIN_VALUE)
    assertThat(service.echoLong(-9007199254740993L)).isEqualTo(-9007199254740993L)
    assertThat(service.echoLong(-9007199254740992L)).isEqualTo(-9007199254740992L)
    assertThat(service.echoLong(-9007199254740991L)).isEqualTo(-9007199254740991L)
    assertThat(service.echoLong(-9007199254740990L)).isEqualTo(-9007199254740990L)
    assertThat(service.echoLong(-1L)).isEqualTo(-1L)
    assertThat(service.echoLong(0L)).isEqualTo(0L)
    assertThat(service.echoLong(1L)).isEqualTo(1L)
    assertThat(service.echoLong(9007199254740990L)).isEqualTo(9007199254740990L)
    assertThat(service.echoLong(9007199254740991L)).isEqualTo(9007199254740991L)
    assertThat(service.echoLong(9007199254740992L)).isEqualTo(9007199254740992L)
    assertThat(service.echoLong(9007199254740993L)).isEqualTo(9007199254740993L)
    assertThat(service.echoLong(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE)
  }
}
