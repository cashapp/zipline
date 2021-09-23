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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class CoroutineEventLoop(
  private val scope: CoroutineScope,
  private val jsPlatform: JsPlatform,
) : EventLoop {
  private val jobs = mutableMapOf<Int, Job>()

  override fun setTimeout(timeoutId: Int, delayMillis: Int) {
    jobs[timeoutId] = scope.launch(start = UNDISPATCHED) {
      delay(delayMillis.toLong())
      jsPlatform.runJob(timeoutId)
    }
  }

  override fun clearTimeout(timeoutId: Int) {
    jobs.remove(timeoutId)?.cancel()
  }
}
