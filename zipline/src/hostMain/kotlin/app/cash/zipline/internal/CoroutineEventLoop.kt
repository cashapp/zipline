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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * We implement scheduled work with raw calls to [CoroutineDispatcher.dispatch] because it prevents
 * recursion. Otherwise, it's easy to unintentionally have `setTimeout(0, ...)` calls that execute
 * immediately, eventually exhausting stack space and crashing the process.
 */
internal class CoroutineEventLoop(
  private val dispatcher: CoroutineDispatcher,
  private val scope: CoroutineScope,
  private val guestService: GuestService,
) {
  private val jobs = mutableMapOf<Int, DelayedJob>()

  fun setTimeout(timeoutId: Int, delayMillis: Int) {
    val job = DelayedJob(timeoutId, delayMillis)
    jobs[timeoutId] = job
    dispatcher.dispatch(scope.coroutineContext, job)
  }

  fun clearTimeout(timeoutId: Int) {
    jobs.remove(timeoutId)?.cancel()
  }

  private inner class DelayedJob(
    val timeoutId: Int,
    val delayMillis: Int,
  ) : Runnable {
    var canceled = false
    var job: Job? = null

    override fun run() {
      if (canceled) return
      this.job = scope.launch(start = UNDISPATCHED) {
        delay(delayMillis.toLong())
        scope.ensureActive() // Necessary as delay() won't detect cancellation if the duration is 0.
        guestService.runJob(timeoutId)
        jobs.remove(timeoutId)
      }
    }

    fun cancel() {
      canceled = true
      job?.cancel()
    }
  }
}
