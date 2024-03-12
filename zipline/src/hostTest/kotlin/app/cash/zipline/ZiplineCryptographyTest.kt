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
import app.cash.zipline.testing.CryptoHasher
import app.cash.zipline.testing.LoggingEventListener
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
import okio.Buffer
import okio.ByteString
import okio.HashingSink

class ZiplineCryptographyTest {
  private val eventListener = LoggingEventListener()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val dispatcher = UnconfinedTestDispatcher()
  private val zipline = Zipline.create(dispatcher, eventListener = eventListener)
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

      override fun sha256(data: ByteArray) = error("unexpected call")
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


  @Test
  fun hashFunctionsBridgeWorks() = runBlocking(dispatcher) {
    if (isLinux) return@runBlocking
    val fakeZiplineCryptographyService = object : ZiplineCryptographyService {
      override fun nextSecureRandomBytes(size: Int) = error("unexpected call")

      override fun sha256(data: ByteArray): String {
        // use okio to compute sha256
        val x = ByteString(data)
        return x.sha256().hex()
      }
    }

    zipline.installCryptographyServiceInternal(fakeZiplineCryptographyService)

    quickjs.evaluate("testing.app.cash.zipline.testing.prepareCryptoHasher()")
    val cryptoHasher = zipline.take<CryptoHasher>("cryptoHasher")

    assertThat(cryptoHasher.sha256("zipline")).isEqualTo("e134c033e88fbff499696afe2aa8744805969081fb2ef10369661c3e640082eb")
    assertThat(cryptoHasher.sha256("app.cash.zipline.cryptography-ziplinecryptographytest-hashFunctionBridgeWorks")).isEqualTo("e1f72c63287f59cc0f7e84933d3b7555116d8d05a8087f98eeb5bf29b616e368")
    assertThat(cryptoHasher.sha256("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
  }

  @Test
  fun hashFunctionsWorksWithNativeImplementation() = runBlocking(dispatcher) {
    if (isLinux) return@runBlocking
    zipline.installCryptographyService()

    quickjs.evaluate("testing.app.cash.zipline.testing.prepareCryptoHasher()")
    val cryptoHasher = zipline.take<CryptoHasher>("cryptoHasher")

    assertThat(cryptoHasher.sha256("zipline")).isEqualTo("e134c033e88fbff499696afe2aa8744805969081fb2ef10369661c3e640082eb")
    assertThat(cryptoHasher.sha256("app.cash.zipline.cryptography-ziplinecryptographytest-hashFunctionBridgeWorks")).isEqualTo("e1f72c63287f59cc0f7e84933d3b7555116d8d05a8087f98eeb5bf29b616e368")
    assertThat(cryptoHasher.sha256("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
  }
}
