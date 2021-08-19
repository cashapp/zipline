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
package app.cash.quickjs.ktbridge

import app.cash.quickjs.QuickJs
import java.util.concurrent.atomic.AtomicInteger

/** The constructor is invoked by compiler-plugin-rewritten code. */
abstract class JsClient<T : Any>(
  protected val jsAdapter: JsAdapter,
  private val webpackModuleName: String,
  private val packageName: String,
  private val propertyName: String,
) : BridgeToJs<T> {

  private val globalName = "ktBridge_${nextGlobalId.getAndIncrement()}"

  override fun get(quickJs: QuickJs): T {
    quickJs.evaluate("this.$globalName = $webpackModuleName.$packageName.$propertyName")
    val internalBridge = quickJs.get(globalName, InternalBridge::class.java)
    return get(internalBridge)
  }

  abstract fun get(internalBridge: InternalBridge): T

  companion object {
    private val nextGlobalId = AtomicInteger(1)
  }
}
