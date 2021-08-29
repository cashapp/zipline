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
package app.cash.quickjs

import kotlin.reflect.KType
import okio.Buffer

/** A bridged interface to pass results from suspending calls. */
@PublishedApi
internal interface SuspendCallback {
  fun success(encodedResponse: ByteArray)

  object Adapter : JsAdapter {
    override fun <T : Any> encode(value: T, sink: Buffer, type: KType) {
      when (type.classifier) {
        ByteArray::class -> sink.write(value as ByteArray)
        else -> error("unexpected type: $type")
      }
    }

    override fun <T : Any> decode(source: Buffer, type: KType): T {
      return when (type.classifier) {
        ByteArray::class -> source.readByteArray() as T
        else -> error("unexpected type: $type")
      }
    }
  }
}
