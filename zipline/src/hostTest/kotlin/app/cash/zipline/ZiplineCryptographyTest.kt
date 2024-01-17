/*
 * Copyright (C) 2024 Cash App
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
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER") // Access :zipline-cryptography internals.

package app.cash.zipline

import app.cash.zipline.cryptography.ZiplineCryptographyService
import app.cash.zipline.cryptography.installCryptographyService
import app.cash.zipline.cryptography.installCryptographyServiceInternal
import app.cash.zipline.testing.RandomStringMaker
import app.cash.zipline.testing.isLinux
import app.cash.zipline.testing.loadTestingJs
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.matches
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class ZiplineCryptographyTest {
  @OptIn(ExperimentalCoroutinesApi::class)
  private val dispatcher = UnconfinedTestDispatcher()
  private val zipline = Zipline.create(dispatcher)
  private val quickjs = zipline.quickJs

  @BeforeTest
  fun setUp() = runBlocking(dispatcher) {
    zipline.loadTestingJs()
  }

  @AfterTest
  fun tearDown() = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test
  fun secureRandomWorks() = runBlocking(dispatcher) {
    if (isLinux) return@runBlocking
    val fakeZiplineCryptographyService = object : ZiplineCryptographyService {
      override fun nextSecureRandomBytes(size: Int): ByteArray {
        return ByteArray(size) { index -> (index + 1).toByte() }
      }
    }

    zipline.installCryptographyServiceInternal(fakeZiplineCryptographyService)

    quickjs.evaluate("testing.app.cash.zipline.testing.prepareRandomStringMaker()")
    val randomStringMaker = zipline.take<RandomStringMaker>("randomStringMaker")
    assertThat(randomStringMaker.randomString()).isEqualTo("[1, 2, 3, 4, 5]")
  }

  @Test
  fun secureRandomWorksWithNativeImplementation() = runBlocking(dispatcher) {
    if (isLinux) return@runBlocking
    zipline.installCryptographyService()

    quickjs.evaluate("testing.app.cash.zipline.testing.prepareRandomStringMaker()")
    val randomStringMaker = zipline.take<RandomStringMaker>("randomStringMaker")
    val randomString = randomStringMaker.randomString()
    assertThat(randomString).matches(Regex("""\[(-?\d+, ){4}-?\d+]"""))
    assertThat(randomString).isNotEqualTo("[1, 2, 3, 4, 5]")
  }
}
