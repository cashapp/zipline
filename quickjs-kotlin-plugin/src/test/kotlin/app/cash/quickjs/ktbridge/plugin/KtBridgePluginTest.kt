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

import app.cash.quickjs.QuickJs
import app.cash.quickjs.testing.EchoRequest
import app.cash.quickjs.testing.EchoResponse
import app.cash.quickjs.testing.EchoService
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.After
import org.junit.Test

/**
 * Confirm bridge calls are rewritten to use `OutboundClientFactory` or `InboundService` as
 * appropriate.
 */
class KtBridgePluginTest {
  private val quickJs = QuickJs.create()

  @After
  fun tearDown() {
    quickJs.close()
  }

  @Test
  fun `set rewritten to receive inbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
          """
        package app.cash.quickjs.testing
        
        import app.cash.quickjs.QuickJs
        
        class TestingEchoService(
          private val greeting: String
        ) : EchoService {
          override fun echo(request: EchoRequest): EchoResponse {
            return EchoResponse("${'$'}greeting from the compiler plugin, ${'$'}{request.message}")
          }
        }
        
        fun prepareJsBridges(quickJs: QuickJs) {
          quickJs.set<EchoService>("helloService", EchoJsAdapter, TestingEchoService("hello"))
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val mainKt = result.classLoader.loadClass("app.cash.quickjs.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", QuickJs::class.java).invoke(null, quickJs)

    val helloService = KtBridgeTestInternals.getEchoClient(quickJs, "helloService")
    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("hello from the compiler plugin, Jesse"))
  }

  @Test
  fun `set rewritten to make outbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
          """
        package app.cash.quickjs.testing
        
        import app.cash.quickjs.QuickJs
        
        fun getHelloService(quickJs: QuickJs): EchoService {
          return quickJs.get("helloService", EchoJsAdapter)
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val testingEchoService = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        return EchoResponse("greetings from the compiler plugin, ${request.message}")
      }
    }
    KtBridgeTestInternals.setEchoService(quickJs, "helloService", testingEchoService)

    val mainKt = result.classLoader.loadClass("app.cash.quickjs.testing.MainKt")
    val helloService = mainKt.getDeclaredMethod("getHelloService", QuickJs::class.java)
      .invoke(null, quickJs) as EchoService

    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("greetings from the compiler plugin, Jesse"))
  }

  @Test
  fun `set type argument is not an interface`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
          """
        package app.cash.quickjs.testing
        
        import app.cash.quickjs.QuickJs
        
        fun prepareJsBridges(quickJs: QuickJs) {
          quickJs.set<TestingEchoService>("helloService", EchoJsAdapter, TestingEchoService)
        }

        object TestingEchoService : EchoService {
          override fun echo(request: EchoRequest): EchoResponse = error("")
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains("(6, 12): The type argument to QuickJs.set() must be an interface type")
  }

  @Test
  fun `get type argument is not an interface`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
          """
        package app.cash.quickjs.testing
        
        import app.cash.quickjs.QuickJs
        
        fun getHelloService(quickJs: QuickJs): String {
          return quickJs.get("helloService", EchoJsAdapter)
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains("(6, 19): The type argument to QuickJs.get() must be an interface type")
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
