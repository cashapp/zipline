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
package app.cash.zipline.testing

import app.cash.zipline.JsAdapter
import kotlin.reflect.KType
import okio.Buffer

interface GenericEchoService<T> {
  fun genericEcho(request: T): List<T>
}

/** Fancier generic adapter with common types for testing. */
object GenericJsAdapter : JsAdapter {
  override fun <T : Any> encode(value: T, sink: Buffer, type: KType) {
    when (type.classifier) {
      String::class -> {
        sink.writeUtf8(value as String)
      }
      List::class -> {
        val list = value as List<*>
        val elementBuffer = Buffer()
        sink.writeInt(list.size)
        for (e in list) {
          encode(e!!, elementBuffer, type.arguments[0].type!!)
          sink.writeInt(elementBuffer.size.toInt())
          sink.writeAll(elementBuffer)
        }
      }
      else -> error("unexpected type: $type")
    }
  }

  override fun <T : Any> decode(source: Buffer, type: KType): T {
    return when (type.classifier) {
      String::class -> source.readUtf8() as T
      List::class -> {
        val result = mutableListOf<Any>()
        val size = source.readInt()
        val elementBuffer = Buffer()
        for (i in 0 until size) {
          val byteCount = source.readInt()
          elementBuffer.write(source, byteCount.toLong())
          result += decode(elementBuffer, type.arguments[0].type!!) as Any
        }
        return result as T
      }
      else -> error("unexpected type: $type")
    }
  }
}

