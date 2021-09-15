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

/**
 * Functions that are built in to regular JavaScript environments like the browser and NodeJS, but
 * must be added to QuickJS.
 *
 * https://developer.mozilla.org/en-US/docs/Web/API/WindowOrWorkerGlobalScope
 */
internal interface HostPlatform {
  // See org.w3c.dom.WindowOrWorkerGlobalScope
  fun setTimeout(timeoutId: Int, delayMillis: Int)

  /** @param level one of `log`, `info`, `warn`, or `error`. */
  fun consoleMessage(level: String, message: String)
}

internal interface JsPlatform {
  fun runJob(timeoutId: Int)
}
