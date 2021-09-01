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

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class JniHostPlatform(
  private val jsPlatform: JsPlatform,
  private val dispatcher: CoroutineDispatcher,
) : HostPlatform {
  override fun setTimeout(timeoutId: Int, delayMillis: Int) {
    val scope = CoroutineScope(EmptyCoroutineContext)
    scope.launch(dispatcher) {
      delay(delayMillis.toLong())
      jsPlatform.runJob(timeoutId)
    }
  }
}

internal fun createHostPlatform(
  ktBridge: KtBridge,
  dispatcher: CoroutineDispatcher,
): HostPlatform {
  val jsPlatform = ktBridge.get<JsPlatform>("app.cash.quickjs.jsPlatform", BuiltInJsAdapter)
  val result = JniHostPlatform(jsPlatform, dispatcher)
  ktBridge.set<HostPlatform>("app.cash.quickjs.hostPlatform", BuiltInJsAdapter, result)
  return result
}
