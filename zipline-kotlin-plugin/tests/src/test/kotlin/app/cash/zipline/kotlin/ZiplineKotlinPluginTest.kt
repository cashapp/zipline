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

package app.cash.zipline.kotlin

import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.EchoZiplineService
import app.cash.zipline.testing.GenericEchoService
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.Ignore
import org.junit.Test

/** Confirm bridge calls are rewritten to use `OutboundBridge` or `InboundBridge` as appropriate. */
class ZiplineKotlinPluginTest {
  @Test
  fun `zipline service rewritten with adapter`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "SampleService.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.ZiplineService

        interface SampleService : ZiplineService {
          fun hello(request: EchoRequest): EchoResponse
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val adapterClass = result.classLoader.loadClass(
      "app.cash.zipline.testing.SampleService\$Companion\$Adapter"
    )
    assertThat(adapterClass).isNotNull()
  }

  @Test
  fun `set rewritten to receive inbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.internal.bridge.Endpoint

        class TestingEchoService(
          private val greeting: String
        ) : EchoZiplineService {
          override fun echo(request: EchoRequest): EchoResponse {
            return EchoResponse("${'$'}greeting from the compiler plugin, ${'$'}{request.message}")
          }
        }

        fun prepareJsBridges(endpoint: Endpoint) {
          endpoint.set<EchoZiplineService>("helloService", TestingEchoService("hello"))
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = ZiplineTestInternals.newEndpointPair()
    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", bridgeA::class.java).invoke(null, bridgeA)

    val helloService = ZiplineTestInternals.getEchoZiplineService(bridgeB, "helloService")
    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("hello from the compiler plugin, Jesse"))
  }

  @Test
  fun `get rewritten to make outbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.internal.bridge.Endpoint

        fun getHelloService(endpoint: Endpoint): EchoZiplineService {
          return endpoint.get("helloService")
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = ZiplineTestInternals.newEndpointPair()

    val testingEchoService = object : EchoZiplineService {
      override fun echo(request: EchoRequest): EchoResponse {
        return EchoResponse("greetings from the compiler plugin, ${request.message}")
      }
    }
    ZiplineTestInternals.setEchoZiplineService(bridgeB, "helloService", testingEchoService)

    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    val helloService = mainKt.getDeclaredMethod("getHelloService", bridgeA::class.java)
      .invoke(null, bridgeA) as EchoZiplineService

    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("greetings from the compiler plugin, Jesse"))
  }

  @Test
  fun `set type argument is not an interface`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.internal.bridge.Endpoint

        fun prepareJsBridges(endpoint: Endpoint) {
          endpoint.set<TestingEchoService>("helloService", TestingEchoService)
        }

        object TestingEchoService : EchoService {
          override fun echo(request: EchoRequest): EchoResponse = error("")
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains("(6, 12): The type argument to Zipline.set() must be an interface type")
  }

  @Test
  fun `get type argument is not an interface`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.ZiplineService
        import app.cash.zipline.internal.bridge.Endpoint

        class Concrete : ZiplineService

        fun getHelloService(endpoint: Endpoint): Concrete {
          return endpoint.get("helloService")
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains("(9, 19): The type argument to Zipline.get() must be an interface type")
  }

  @Test
  @Ignore("generics are broken after the ZiplineService refactoring")
  fun `generic service`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.internal.bridge.Endpoint

        class TestingGenericEchoService : GenericEchoService<String> {
          override fun genericEcho(request: String): List<String> {
            return listOf("received a generic ${'$'}request!")
          }
        }

        fun prepareJsBridges(endpoint: Endpoint) {
          endpoint.set<GenericEchoService<String>>(
            "genericService",
            TestingGenericEchoService()
          )
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = ZiplineTestInternals.newEndpointPair()
    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", bridgeA::class.java).invoke(null, bridgeA)

    val helloService = ZiplineTestInternals.getGenericEchoService(bridgeB, "genericService")
    assertThat(helloService.genericEcho("Jesse")).containsExactly("received a generic Jesse!")
  }

  @Test
  @Ignore("generics are broken after the ZiplineService refactoring")
  fun `generic client`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.internal.bridge.Endpoint

        fun getGenericService(endpoint: Endpoint): GenericEchoService<String> {
          return endpoint.get("genericService")
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = ZiplineTestInternals.newEndpointPair()

    val testingService = object : GenericEchoService<String> {
      override fun genericEcho(request: String): List<String> {
        return listOf("received a generic $request!")
      }
    }
    ZiplineTestInternals.setGenericEchoService(bridgeB, "genericService", testingService)

    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    val service = mainKt.getDeclaredMethod("getGenericService", bridgeA::class.java)
      .invoke(null, bridgeA) as GenericEchoService<String>

    assertThat(service.genericEcho("Jesse"))
      .containsExactly("received a generic Jesse!")
  }

  @Test
  fun `set anonymous class`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.internal.bridge.Endpoint

        fun prepareJsBridges(endpoint: Endpoint) {
          endpoint.set<EchoService>("helloService", object : EchoService {
            override fun echo(request: EchoRequest): EchoResponse {
              return EchoResponse("hello from anonymous, ${'$'}{request.message}")
            }
          })
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = ZiplineTestInternals.newEndpointPair()
    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", bridgeA::class.java).invoke(null, bridgeA)

    val helloService = ZiplineTestInternals.getEchoClient(bridgeB, "helloService")
    assertThat(helloService.echo(EchoRequest("Alec")))
      .isEqualTo(EchoResponse("hello from anonymous, Alec"))
  }
}

fun compile(
  sourceFiles: List<SourceFile>,
  plugin: ComponentRegistrar = ZiplineComponentRegistrar(),
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
  plugin: ComponentRegistrar = ZiplineComponentRegistrar(),
): KotlinCompilation.Result {
  return compile(listOf(sourceFile), plugin)
}
