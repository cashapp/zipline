/*
 * Copyright (C) 2025 Cash App
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

import app.cash.zipline.testing.Clock
import app.cash.zipline.testing.HttpActionService
import app.cash.zipline.testing.HttpRequest
import app.cash.zipline.testing.HttpResponse
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Before
import org.junit.Test

class OrionTest {
  private val wasmFile = "../zipline-testing/build/compileSync/wasmWasi/main/developmentExecutable/kotlin/zipline-root-zipline-testing-wasm-wasi.wasm".toPath()

  private val fileSystem = FileSystem.SYSTEM
  private val dispatcher = StandardTestDispatcher()
  private val zipline = Zipline.create(
    dispatcher,
    eventListener = object : EventListener() {
      override fun callStart(zipline: Zipline, call: Call): Any? {
        println(call.encodedCall)
        return null
      }

      override fun callEnd(zipline: Zipline, call: Call, result: CallResult, startValue: Any?) {
        println(result.encodedResult)
      }
    }
  )

  @Before
  fun setUp() = runTest(dispatcher) {
    val wasm = fileSystem.read(wasmFile) { readByteArray() }
    zipline.loadWasmModule(wasm)
  }

  @After
  fun tearDown() = runTest(dispatcher) {
    zipline.close()
  }

  @Test
  fun jvmCallJsService() = runTest(dispatcher) {
    zipline.wasmFunction("_initialize")!!.invoke(listOf())
    val a = zipline.wasmFunction("prepareOrionBridges")!!.invoke(listOf())

    println(a)

    val httpActionService = zipline.take<HttpActionService>("httpActionService")
    zipline.bind<Clock>("clock", object : Clock {
      override fun now(): Long {
        return 1L
      }
    })
    assertThat(httpActionService.execute(HttpRequest("Jake")))
      .isEqualTo(HttpResponse("hello from JavaScript, Jake, the time is 1"))
  }
}
