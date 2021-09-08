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

package app.cash.zipline.ktbridge.plugin

import app.cash.zipline.internal.bridge.KtBridge
import app.cash.zipline.internal.bridge.OutboundCall
import app.cash.zipline.internal.bridge.OutboundClientFactory
import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoSerializersModule
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.GenericEchoService
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Confirm bridge calls are rewritten to use `OutboundClientFactory` or `InboundService` as
 * appropriate.
 */
class KtBridgePluginTest {
  @Test
  fun `ktBridge set rewritten to receive inbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing
        
        import app.cash.zipline.internal.bridge.KtBridge
        
        class TestingEchoService(
          private val greeting: String
        ) : EchoService {
          override fun echo(request: EchoRequest): EchoResponse {
            return EchoResponse("${'$'}greeting from the compiler plugin, ${'$'}{request.message}")
          }
        }
        
        fun prepareJsBridges(ktBridge: KtBridge) {
          ktBridge.set<EchoService>("helloService", EchoSerializersModule, TestingEchoService("hello"))
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = KtBridgeTestInternals.newKtBridgePair()
    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", bridgeA::class.java).invoke(null, bridgeA)

    val helloService = KtBridgeTestInternals.getEchoClient(bridgeB, "helloService")
    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("hello from the compiler plugin, Jesse"))
  }

  @Test
  fun `ktBridge set rewritten to make outbound calls`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing
        
        import app.cash.zipline.internal.bridge.KtBridge
//        import app.cash.zipline.internal.bridge.OutboundCall
//        import app.cash.zipline.internal.bridge.OutboundClientFactory
//        import app.cash.zipline.testing.EchoRequest
//        import app.cash.zipline.testing.EchoResponse
//        import app.cash.zipline.testing.EchoSerializersModule
//        import app.cash.zipline.testing.EchoService
//        import app.cash.zipline.testing.GenericEchoService
//        import kotlinx.serialization.KSerializer
//        import kotlinx.serialization.modules.SerializersModule
//        import kotlinx.serialization.serializer
        
        fun getHelloService(ktBridge: KtBridge): EchoService {
//          val serializersModule: SerializersModule = EchoSerializersModule
//          val x = ktBridge.get("hello", object : OutboundClientFactory<EchoService>(serializersModule) {
//            val parameterSerializer: KSerializer<EchoRequest> = this.serializersModule.serializer<EchoRequest>()
//            val resultSerializer: KSerializer<EchoResponse> = this.serializersModule.serializer<EchoResponse>()
//            override fun create(callFactory: OutboundCall.Factory): EchoService {
//              return object : EchoService {
//                override fun echo(request: EchoRequest): EchoResponse {
//                  val outboundCall: OutboundCall = callFactory.create("echo", 1)
//                  outboundCall.parameter(parameterSerializer, request)
//                  return outboundCall.invoke(resultSerializer)
//                }
//              }
//            }
//          })

          return ktBridge.get("helloService", EchoSerializersModule)
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = KtBridgeTestInternals.newKtBridgePair()

    val testingEchoService = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        return EchoResponse("greetings from the compiler plugin, ${request.message}")
      }
    }
    KtBridgeTestInternals.setEchoService(bridgeB, "helloService", testingEchoService)

    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    val helloService = mainKt.getDeclaredMethod("getHelloService", bridgeA::class.java)
      .invoke(null, bridgeA) as EchoService

    assertThat(helloService.echo(EchoRequest("Jesse")))
      .isEqualTo(EchoResponse("greetings from the compiler plugin, Jesse"))
  }

  @Test
  fun `ktBridge set type argument is not an interface`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing
        
        import app.cash.zipline.internal.bridge.KtBridge
        
        fun prepareJsBridges(ktBridge: KtBridge) {
          ktBridge.set<TestingEchoService>("helloService", EchoSerializersModule, TestingEchoService)
        }

        object TestingEchoService : EchoService {
          override fun echo(request: EchoRequest): EchoResponse = error("")
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains("(6, 12): The type argument to KtBridge.set() must be an interface type")
  }

  @Test
  fun `ktBridge get type argument is not an interface`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing
        
        import app.cash.zipline.internal.bridge.KtBridge
        
        fun getHelloService(ktBridge: KtBridge): String {
          return ktBridge.get("helloService", EchoSerializersModule)
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
    assertThat(result.messages)
      .contains("(6, 19): The type argument to KtBridge.get() must be an interface type")
  }

  @Test
  fun `generic service`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing
        
        import app.cash.zipline.internal.bridge.KtBridge
        
        class TestingGenericEchoService : GenericEchoService<String> {
          override fun genericEcho(request: String): List<String> {
            return listOf("received a generic ${'$'}request!")
          }
        }
        
        fun prepareJsBridges(ktBridge: KtBridge) {
          ktBridge.set<GenericEchoService<String>>(
            "genericService",
            EchoSerializersModule,
            TestingGenericEchoService()
          )
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = KtBridgeTestInternals.newKtBridgePair()
    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    mainKt.getDeclaredMethod("prepareJsBridges", bridgeA::class.java).invoke(null, bridgeA)

    val helloService = KtBridgeTestInternals.getGenericEchoService(bridgeB, "genericService")
    assertThat(helloService.genericEcho("Jesse")).containsExactly("received a generic Jesse!")
  }

  @Test
  fun `generic client`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt",
        """
        package app.cash.zipline.testing
        
        import app.cash.zipline.internal.bridge.KtBridge
        
        fun getGenericService(ktBridge: KtBridge): GenericEchoService<String> {
          return ktBridge.get("genericService", EchoSerializersModule)
        }
        """
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

    val (bridgeA, bridgeB) = KtBridgeTestInternals.newKtBridgePair()

    val testingService = object : GenericEchoService<String> {
      override fun genericEcho(request: String): List<String> {
        return listOf("received a generic $request!")
      }
    }
    KtBridgeTestInternals.setGenericEchoService(bridgeB, "genericService", testingService)

    val mainKt = result.classLoader.loadClass("app.cash.zipline.testing.MainKt")
    val service = mainKt.getDeclaredMethod("getGenericService", bridgeA::class.java)
      .invoke(null, bridgeA) as GenericEchoService<String>

    assertThat(service.genericEcho("Jesse"))
      .containsExactly("received a generic Jesse!")
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
