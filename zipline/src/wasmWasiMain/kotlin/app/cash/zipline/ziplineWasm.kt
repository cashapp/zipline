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

import app.cash.zipline.internal.GuestService
import app.cash.zipline.internal.HostService
import app.cash.zipline.internal.ZIPLINE_GUEST_NAME
import app.cash.zipline.internal.ZIPLINE_HOST_NAME
import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.internal.bridge.GuestBridgeCallChannel
import app.cash.zipline.internal.bridge.OutboundCallHandler
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass
import kotlin.reflect.cast

actual class Zipline internal constructor(userSerializersModule: SerializersModule) {
  private val eventListener = object : Endpoint.EventListener() {
    override fun serviceLeaked(name: String) {
      host.serviceLeaked(name)
    }
  }

  @OptIn(DelicateCoroutinesApi::class) // GlobalScope is appropriate; Zipline is global per JS VM.
  internal val endpoint = Endpoint(
    scope = GlobalScope,
    userSerializersModule = userSerializersModule,
    eventListener = eventListener,
    outboundChannel = GuestBridgeCallChannel,
    oppositeProvider = {
      host
    },
  )

  actual val json = endpoint.json

  internal actual val serviceNames: Set<String>
    get() = endpoint.serviceNames

  internal actual val clientNames: Set<String>
    get() = host.serviceNames

  internal val host: HostService = endpoint.take(ZIPLINE_HOST_NAME)

  private val attachments = mutableMapOf<KClass<*>, Any>()

  actual fun <T : ZiplineService> bind(name: String, instance: T) {
    error("unexpected call to Zipline.bind: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> bind(
    name: String,
    service: T,
    adapter: ZiplineServiceAdapter<T>,
  ) {
    endpoint.bind(name, service, adapter)
  }

  actual fun <T : ZiplineService> take(
    name: String,
    scope: ZiplineScope,
  ): T {
    println("calling take $name with no adapter")

    take(name, scope, object : ZiplineServiceAdapter<T>() {
      override val serializers: List<KSerializer<*>>
        get() = TODO("Not yet implemented")
      override val serialName: String
        get() = TODO("Not yet implemented")
      override val simpleName: String
        get() = TODO("Not yet implemented")

      override fun ziplineFunctions(serializersModule: SerializersModule): List<ZiplineFunction<T>> {
        TODO("Not yet implemented")
      }

      override fun outboundService(callHandler: OutboundCallHandler): T {
        TODO("Not yet implemented")
      }
    })

    return null as T
//    error("unexpected call to Zipline.take: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> take(
    name: String,
    scope: ZiplineScope = ZiplineScope(),
    adapter: ZiplineServiceAdapter<T>,
  ): T {
    println("calling take $name $adapter")
    return endpoint.take(name, scope, adapter)
  }

  actual fun <T : Any> getOrPutAttachment(key: KClass<T>, compute: () -> T): T {
    val value = attachments.getOrPut(key, compute)
    return key.cast(value)
  }

  companion object {
    fun get(serializersModule: SerializersModule = EmptySerializersModule()): Zipline {
      println("get()")
      theOnlyZipline?.let { theOnlyZipline ->
        require(serializersModule == theOnlyZipline.endpoint.userSerializersModule) {
          "get() called multiple times with non-equal serializersModule instances"
        }
        return theOnlyZipline
      }

      println("get() 2")
      return Zipline(serializersModule)
        .apply {
          println("get() 3")
          bind<GuestService>(ZIPLINE_GUEST_NAME, GlobalBridge)
          theOnlyZipline = this
        }
    }
  }
}

/**
 * The global singleton instance. In Kotlin/JS we require a global singleton per runtime to make it
 * easy for the host platform to find its Zipline instance.
 *
 * Note that the host platform won't necessarily have a singleton Zipline; it'll have one Zipline
 * instance per QuickJS VM.
 */
var theOnlyZipline: Zipline? = null
