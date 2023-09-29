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
package app.cash.zipline.internal.bridge

import kotlin.js.JsName

/**
 * Be careful changing these. They're inlined manually in js() blocks to workaround this issue:
 * https://github.com/cashapp/zipline/issues/900
 */
internal const val INBOUND_CHANNEL_NAME = "app_cash_zipline_inboundChannel"
internal const val OUTBOUND_CHANNEL_NAME = "app_cash_zipline_outboundChannel"

@PublishedApi
internal interface CallChannel {
  /**
   * Internal function used to bridge method calls from either Kotlin/JVM or Kotlin/Native to
   * Kotlin/JS.
   */
  @JsName("call")
  fun call(callJson: String): String

  /**
   * Remove [instanceName] from the receiver. After making this call it is an error to make calls
   * with this name.
   *
   * @return true if the instance name existed.
   */
  @JsName("disconnect")
  fun disconnect(instanceName: String): Boolean
}
