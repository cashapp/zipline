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
package app.cash.zipline.internal

import app.cash.zipline.ZiplineService

internal const val eventLoopName = "zipline/event_loop"
internal const val consoleName = "zipline/console"
internal const val jsPlatformName = "zipline/js"

internal interface EventLoop : ZiplineService {
  fun setTimeout(timeoutId: Int, delayMillis: Int)
  fun clearTimeout(timeoutId: Int)
}

internal interface Console : ZiplineService {
  /** @param level one of `log`, `info`, `warn`, or `error`. */
  fun log(level: String, message: String, throwable: Throwable?)
}

internal interface JsPlatform : ZiplineService {
  fun runJob(timeoutId: Int)
}
