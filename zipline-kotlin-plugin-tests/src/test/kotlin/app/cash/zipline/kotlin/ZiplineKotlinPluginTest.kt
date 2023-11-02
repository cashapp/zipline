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
import app.cash.zipline.testing.EchoZiplineService
import app.cash.zipline.testing.GenericEchoService
import app.cash.zipline.testing.ZiplineTestInternals
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlin.test.assertEquals
import kotlinx.serialization.KSerializer
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test

/** Confirm bridge calls are rewritten to use `OutboundBridge` or `InboundBridge` as appropriate. */
@OptIn(ExperimentalCompilerApi::class)
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
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val adapterClass = result.classLoader.loadClass(
      "app.cash.zipline.testing.SampleService\$Companion\$Adapter",
    )
    assertThat(adapterClass).isNotNull()
    assertThat(adapterClass.interfaces).containsExactly(KSerializer::class.java)
  }

  @Test
  fun `bind rewritten to receive inbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        class TestingEchoService(
          private val greeting: String
        ) : EchoZiplineService {
          override fun echo(request: EchoRequest): EchoResponse {
            return EchoResponse("${'$'}greeting from the compiler plugin, ${'$'}{request.message}")
          }
        }

        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
        private fun prepareJsBridges(endpoint: app.cash.zipline.internal.bridge.Endpoint) {
          endpoint.bind<EchoZiplineService>("helloService", TestingEchoService("hello"))
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = ZiplineTestInternals.newEndpointPair()
    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", bridgeA::class.java).run {
      isAccessible = true
      invoke(null, bridgeA)
    }

    val helloService = ZiplineTestInternals.takeEchoZiplineService(bridgeB, "helloService")
    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("hello from the compiler plugin, Jesse"))
  }

  @Test
  fun `take rewritten to make outbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
        private fun takeHelloService(
          endpoint: app.cash.zipline.internal.bridge.Endpoint,
        ): EchoZiplineService {
          return endpoint.take("helloService")
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = ZiplineTestInternals.newEndpointPair()

    val testingEchoService = object : EchoZiplineService {
      override fun echo(request: EchoRequest): EchoResponse {
        return EchoResponse("greetings from the compiler plugin, ${request.message}")
      }
    }
    ZiplineTestInternals.bindEchoZiplineService(bridgeB, "helloService", testingEchoService)

    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    val helloService = mainKt.getDeclaredMethod("takeHelloService", bridgeA::class.java).run {
      isAccessible = true
      invoke(null, bridgeA) as EchoZiplineService
    }

    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("greetings from the compiler plugin, Jesse"))
  }

  @Test
  fun `bind type argument is not an interface`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
        private fun prepareJsBridges(
          endpoint: app.cash.zipline.internal.bridge.Endpoint,
        ) {
          endpoint.bind<TestingEchoService>("helloService", TestingEchoService)
        }

        object TestingEchoService : EchoService {
          override fun echo(request: EchoRequest): EchoResponse = error("")
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains(":7:12 The type argument to Zipline.bind() must be an interface type")
  }

  @Test
  fun `take type argument is not an interface`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.ZiplineService

        class Concrete : ZiplineService

        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
        private fun takeHelloService(
          endpoint: app.cash.zipline.internal.bridge.Endpoint,
        ): Concrete {
          return endpoint.take("helloService")
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains(":11:19 The type argument to Zipline.take() must be an interface type")
  }

  @Test
  fun `generic service`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        class TestingGenericEchoService : GenericEchoService<String> {
          override fun genericEcho(request: String): List<String> {
            return listOf("received a generic ${'$'}request!")
          }
        }

        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
        private fun prepareJsBridges(
          endpoint: app.cash.zipline.internal.bridge.Endpoint,
        ) {
          endpoint.bind<GenericEchoService<String>>(
            "genericService",
            TestingGenericEchoService()
          )
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = ZiplineTestInternals.newEndpointPair()
    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", bridgeA::class.java).run {
      isAccessible = true
      invoke(null, bridgeA)
    }

    val helloService = ZiplineTestInternals.takeGenericEchoService(bridgeB, "genericService")
    assertThat(helloService.genericEcho("Jesse")).containsExactly("received a generic Jesse!")
  }

  @Test
  fun `generic client`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
        private fun takeGenericService(
          endpoint: app.cash.zipline.internal.bridge.Endpoint
        ): GenericEchoService<String> {
          return endpoint.take("genericService")
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = ZiplineTestInternals.newEndpointPair()

    val testingService = object : GenericEchoService<String> {
      override fun genericEcho(request: String): List<String> {
        return listOf("received a generic $request!")
      }
    }
    ZiplineTestInternals.bindGenericEchoService(bridgeB, "genericService", testingService)

    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    val service = mainKt.getDeclaredMethod("takeGenericService", bridgeA::class.java).run {
      isAccessible = true
      invoke(null, bridgeA) as GenericEchoService<String>
    }

    assertThat(service.genericEcho("Jesse"))
      .containsExactly("received a generic Jesse!")
  }

  @Test
  fun `bind anonymous class`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
        private fun prepareJsBridges(endpoint: app.cash.zipline.internal.bridge.Endpoint) {
          endpoint.bind<EchoService>("helloService", object : EchoService {
            override fun echo(request: EchoRequest): EchoResponse {
              return EchoResponse("hello from anonymous, ${'$'}{request.message}")
            }
          })
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = ZiplineTestInternals.newEndpointPair()
    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", bridgeA::class.java).run {
      isAccessible = true
      invoke(null, bridgeA)
    }

    val helloService = ZiplineTestInternals.takeEchoClient(bridgeB, "helloService")
    assertThat(helloService.echo(EchoRequest("Alec")))
      .isEqualTo(EchoResponse("hello from anonymous, Alec"))
  }

  @Test
  fun `zipline service adapter generated with flow`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "SampleService.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.ZiplineService
        import kotlinx.coroutines.flow.Flow

        interface SampleService : ZiplineService {
          fun hello(request: Flow<EchoRequest>): Flow<EchoResponse>
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
  }

  @Test
  fun `get service serializer with a kclass`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import kotlinx.serialization.KSerializer
        import app.cash.zipline.ziplineServiceSerializer

        fun createServiceSerializer(): KSerializer<EchoZiplineService> {
          return ziplineServiceSerializer(EchoZiplineService::class, listOf())
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    val serializer = mainKt.getDeclaredMethod("createServiceSerializer")
      .invoke(null)
    assertThat(serializer).isInstanceOf(KSerializer::class.java)
  }

  @Test
  fun `service has val and var property`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "SampleService.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.ZiplineService

        interface SampleService : ZiplineService {
          val count: Int
          var total: Int
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val adapterClass = result.classLoader.loadClass(
      "app.cash.zipline.testing.SampleService\$Companion\$Adapter",
    )
    assertThat(adapterClass).isNotNull()
    assertThat(adapterClass.interfaces).containsExactly(KSerializer::class.java)
  }

  @Test
  fun `interfaces cannot extend ZiplineScoped`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.ZiplineScoped

        interface SomeInterface : ZiplineScoped
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains(
        ":5:1 Only classes may implement ZiplineScoped, but " +
        "app.cash.zipline.testing.SomeInterface is an interface",
      )
  }

  @Test
  fun `service has function with overloads`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing

        import app.cash.zipline.ZiplineService

        interface ParentService : ZiplineService {
            fun test(first: Int)
        }

        interface ChildService : ParentService, ZiplineService {
            fun test(first: Int, second: Int)
        }
        """,
      ),
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
  }
}

@ExperimentalCompilerApi
fun compile(
  sourceFiles: List<SourceFile>,
  plugin: CompilerPluginRegistrar = ZiplineCompilerPluginRegistrar(),
): JvmCompilationResult {
  return KotlinCompilation().apply {
    sources = sourceFiles
    compilerPluginRegistrars = listOf(plugin)
    inheritClassPath = true
  }.compile()
}

@ExperimentalCompilerApi
fun compile(
  sourceFile: SourceFile,
  plugin: CompilerPluginRegistrar = ZiplineCompilerPluginRegistrar(),
): JvmCompilationResult {
  return compile(listOf(sourceFile), plugin)
}
