/*
 * Copyright (C) 2022 Block, Inc.
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
package app.cash.zipline

import app.cash.zipline.internal.ziplineInternalPrefix

class LoggingEventListener : EventListener() {
  private var nextCallId = 1
  private val log = ArrayDeque<LogEntry>()

  private data class LogEntry(
    val serviceName: String,
    val log: String
  )

  override fun callStart(name: String, service: ZiplineService, functionName: String, args: List<Any?>): Any? {
    val callId = nextCallId++
    log += LogEntry(name, "callStart $callId $name $functionName $args")
    return callId
  }

  override fun callEnd(name: String, service: ZiplineService, functionName: String, args: List<Any?>, result: Result<Any?>, callStartResult: Any?) {
    log += LogEntry(name, "callEnd $callStartResult $name $functionName $args $result")
  }

  override fun serviceLeaked(name: String) {
    log += LogEntry(name, "serviceLeaked($name)")
  }

  fun take(includeZiplineInternal: Boolean = false): String {
    while (true) {
      val (serviceName, log) = log.removeFirst()
      if (!includeZiplineInternal && serviceName.startsWith(ziplineInternalPrefix)) continue
      return log
    }
  }
}
