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

internal class RealJsPlatform(
  private val hostPlatform: HostPlatform
) : JsPlatform {
  private var nextTimeoutId = 1
  private val jobs = mutableMapOf<Int, Job>()

  override fun runJob(timeoutId: Int) {
    val job = jobs.remove(timeoutId) ?: return
    job.handler.apply(null, job.arguments)
  }

  @JsName("setTimeout")
  fun setTimeout(handler: dynamic, timeout: Int, vararg arguments: Any?): Int {
    val timeoutId = nextTimeoutId++
    jobs[timeoutId] = Job(handler, arguments)
    hostPlatform.setTimeout(timeoutId, timeout)
    return timeoutId
  }

  @JsName("clearTimeout")
  fun clearTimeout(handle: Int) {
    jobs.remove(handle)
    // TODO(jwilson): tell the host platform to clear the timeout.
  }

  private class Job(
    val handler: dynamic,
    val arguments: Array<out Any?>
  )
}

internal val jsPlatform: JsPlatform = run {
  val hostPlatform = ktBridge.get<HostPlatform>(
    "app.cash.quickjs.hostPlatform",
    BuiltInJsAdapter
  )

  val jsPlatform = RealJsPlatform(hostPlatform)

  // Create global functions for JavaScript callers.
  js("""
    globalThis.setTimeout = function(handler, delay, args) {
      return jsPlatform.setTimeout(handler, delay, args);
    };
    globalThis.clearTimeout = function(timeoutID) {
      return jsPlatform.clearTimeout(timeoutID);
    };
    """)

  // Create a bridge into this platform for non-JavaScript callers.
  ktBridge.set<JsPlatform>("app.cash.quickjs.jsPlatform", BuiltInJsAdapter, jsPlatform)
  return@run jsPlatform
}
