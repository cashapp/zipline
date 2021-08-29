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

fun createKtBridge(quickJs: QuickJs): KtBridge {
  // Lazily fetch the bridge to call them.
  val jsInboundBridge = object : InternalBridge {
    val delegate: InternalBridge by lazy(mode = LazyThreadSafetyMode.NONE) {
      quickJs.get(
        name = "app_cash_quickjs_ktbridge_inboundBridge",
        type = InternalBridge::class.java
      )
    }

    override fun invoke(
      instanceName: String,
      funName: String,
      encodedArguments: ByteArray
    ): ByteArray {
      return delegate.invoke(instanceName, funName, encodedArguments)
    }

    override fun invokeSuspending(
      instanceName: String,
      funName: String,
      encodedArguments: ByteArray,
      callbackName: String
    ) {
      return delegate.invokeSuspending(instanceName, funName, encodedArguments, callbackName)
    }
  }

  val ktBridge = KtBridge(outboundBridge = jsInboundBridge)

  // Eagerly publish the bridge so they can call us.
  quickJs.set(
    name = "app_cash_quickjs_ktbridge_outboundBridge",
    type = InternalBridge::class.java,
    instance = ktBridge.inboundBridge
  )

  return ktBridge
}
