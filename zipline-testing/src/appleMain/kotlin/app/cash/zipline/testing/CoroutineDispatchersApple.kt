/*
 * Copyright (C) 2023 Cash App
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

import kotlin.coroutines.CoroutineContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSThread

@OptIn(ExperimentalForeignApi::class)
@ExperimentalCoroutinesApi
actual fun singleThreadCoroutineDispatcher(
  name: String,
  stackSize: Int,
): CloseableCoroutineDispatcher {
  val channel = Channel<Runnable?>(capacity = Channel.UNLIMITED)

  val thread = NSThread {
    runBlocking {
      while (true) {
        val runnable = channel.receive() ?: break
        runnable.run()
      }
    }
  }.apply {
    this.name = name
    this.stackSize = stackSize.convert()
  }

  thread.start()

  return object : CloseableCoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      channel.trySend(block)
    }

    override fun close() {
      channel.trySend(null)
    }
  }
}
