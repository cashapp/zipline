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

package app.cash.quickjs.ktbridge.plugin

import app.cash.quickjs.ktbridge.InboundCall
import app.cash.quickjs.ktbridge.InternalBridge
import app.cash.quickjs.ktbridge.JsClient
import app.cash.quickjs.ktbridge.OutboundCall
import app.cash.quickjs.ktbridge.createJsService
import app.cash.quickjs.ktbridge.testing.EchoJsAdapter
import app.cash.quickjs.ktbridge.testing.EchoRequest
import app.cash.quickjs.ktbridge.testing.EchoResponse
import app.cash.quickjs.ktbridge.testing.EchoService
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.Test

/**
 * This test exercises the compiler plugin on the JVM only. This is easier to integrate for testing
 * and mostly representative of real-world behavior.
 */
class KtBridgePluginTest {
  @Test
  fun `createJsService rewritten to receive inbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt", """
package app.cash.quickjs.ktbridge.testing

import app.cash.quickjs.ktbridge.createJsService

class TestingEchoService(
  private val greeting: String
) : EchoService {
  override fun echo(request: EchoRequest): EchoResponse {
    return EchoResponse("${'$'}greeting from the compiler plugin, ${'$'}{request.message}")
  }
}

val helloService = createJsService(EchoJsAdapter, TestingEchoService("hello"))
"""
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val mainKt = result.classLoader.loadClass("app.cash.quickjs.ktbridge.testing.MainKt")
    val helloServiceBridge = mainKt.getDeclaredMethod("getHelloService").invoke(null)
    val helloService = internalBridgeToEchoClient(helloServiceBridge as InternalBridge)

    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("hello from the compiler plugin, Jesse"))
  }

  @Test
  fun `createJsClient rewritten to make outbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt", """
package app.cash.quickjs.ktbridge.testing

import app.cash.quickjs.ktbridge.BridgeToJs
import app.cash.quickjs.ktbridge.createJsClient

val helloService: BridgeToJs<EchoService> = createJsClient<EchoService>(
  jsAdapter = EchoJsAdapter,
  webpackModuleName = "testing",
)
"""
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val testingEchoService = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        return EchoResponse("greetings from the compiler plugin, ${request.message}")
      }
    }

    val mainKt = result.classLoader.loadClass("app.cash.quickjs.ktbridge.testing.MainKt")
    val helloServiceBridge = mainKt.getDeclaredMethod("getHelloService")
      .invoke(null) as JsClient<EchoService>
    val helloService = helloServiceBridge.get(echoServiceToInternalBridge(testingEchoService))

    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("greetings from the compiler plugin, Jesse"))
  }

  /** Manually adapt [InternalBridge] to [EchoService]. In non-test code this is generated. */
  private fun internalBridgeToEchoClient(internalBridge: InternalBridge): EchoService {
    return object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        val outboundCall = OutboundCall(EchoJsAdapter, internalBridge, "echo", 1)
        outboundCall.parameter(request)
        return outboundCall.invoke()
      }
    }
  }

  /** Manually adapt [EchoService] to [InternalBridge]. In non-test code this is generated. */
  private fun echoServiceToInternalBridge(service: EchoService): InternalBridge {
    return createJsService(
      jsAdapter = EchoJsAdapter,
      service = service,
      block = fun(inboundCall: InboundCall<EchoService>): ByteArray {
        return when {
          inboundCall.funName == "echo" -> {
            inboundCall.result(
              inboundCall.service.echo(
                inboundCall.parameter()
              )
            )
          }
          else -> inboundCall.unexpectedFunction()
        }
      }
    ) as InternalBridge
  }
}

fun compile(
  sourceFiles: List<SourceFile>,
  plugin: ComponentRegistrar = KtBridgeComponentRegistrar(),
): KotlinCompilation.Result {
  return KotlinCompilation().apply {
    sources = sourceFiles
    useIR = true
    compilerPlugins = listOf(plugin)
    inheritClassPath = true
  }.compile()
}

fun compile(
  sourceFile: SourceFile,
  plugin: ComponentRegistrar = KtBridgeComponentRegistrar(),
): KotlinCompilation.Result {
  return compile(listOf(sourceFile), plugin)
}
