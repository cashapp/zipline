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

import app.cash.zipline.internal.Console
import app.cash.zipline.internal.EventListenerService
import app.cash.zipline.internal.EventLoop
import app.cash.zipline.internal.JsPlatform
import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import app.cash.zipline.internal.bridge.outboundChannelName
import app.cash.zipline.internal.consoleName
import app.cash.zipline.internal.eventListenerName
import app.cash.zipline.internal.eventLoopName
import app.cash.zipline.internal.jsPlatformName
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

actual class Zipline internal constructor(userSerializersModule: SerializersModule) {
  private val eventListener = object : EventListener() {
    override fun serviceLeaked(name: String) {
      eventListenerService.serviceLeaked(name)
    }
  }

  internal val endpoint = Endpoint(
    scope = GlobalScope,
    userSerializersModule = userSerializersModule,
    eventListener = eventListener,
    outboundChannel = object : CallChannel {
      /** Lazily fetch the channel to call out. */
      @Suppress("UnsafeCastFromDynamic")
      private val jsOutboundChannel: CallChannel
        get() = js("globalThis.$outboundChannelName")

      override fun serviceNamesArray(): Array<String> {
        return jsOutboundChannel.serviceNamesArray()
      }

      override fun call(callJson: String): String {
        return jsOutboundChannel.call(callJson)
      }

      override fun disconnect(instanceName: String): Boolean {
        return jsOutboundChannel.disconnect(instanceName)
      }
    }
  )

  actual val json = endpoint.json

  actual val serviceNames: Set<String>
    get() = endpoint.serviceNames

  actual val clientNames: Set<String>
    get() = endpoint.clientNames

  internal val eventLoop: EventLoop = endpoint.take(eventLoopName)
  internal val eventListenerService: EventListenerService = endpoint.take(eventListenerName)
  internal val console: Console = endpoint.take(consoleName)

  actual fun <T : ZiplineService> bind(name: String, instance: T) {
    error("unexpected call to Zipline.bind: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> bind(
    name: String,
    service: T,
    adapter: ZiplineServiceAdapter<T>
  ) {
    endpoint.bind(name, service, adapter)
  }

  actual fun <T : ZiplineService> take(name: String): T {
    error("unexpected call to Zipline.take: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> take(name: String, adapter: ZiplineServiceAdapter<T>): T {
    return endpoint.take(name, adapter)
  }

  companion object {
    fun get(serializersModule: SerializersModule = EmptySerializersModule): Zipline {
      val theOnlyZipline = THE_ONLY_ZIPLINE
      if (theOnlyZipline != null) {
        require(serializersModule == theOnlyZipline.endpoint.userSerializersModule) {
          "get() called multiple times with non-equal serializersModule instances"
        }
        return theOnlyZipline
      }

      return Zipline(serializersModule)
        .apply {
          bind<JsPlatform>(jsPlatformName, GlobalBridge)
          THE_ONLY_ZIPLINE = this
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
internal var THE_ONLY_ZIPLINE: Zipline? = null
