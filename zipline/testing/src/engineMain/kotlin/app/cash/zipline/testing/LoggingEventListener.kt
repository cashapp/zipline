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
package app.cash.zipline.testing

import app.cash.zipline.EventListener
import app.cash.zipline.ZiplineCall
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.ziplineInternalPrefix

class LoggingEventListener : EventListener() {
  private var nextCallId = 1
  private val log = ArrayDeque<LogEntry>()

  override fun bindService(name: String, service: ZiplineService) {
    log += LogEntry(
      serviceName = name,
      log = "bindService $name"
    )
  }

  override fun takeService(name: String, service: ZiplineService) {
    log += LogEntry(
      serviceName = name,
      log = "takeService $name"
    )
  }

  override fun callStart(call: ZiplineCall): Any? {
    val callId = nextCallId++
    log += LogEntry(
      serviceName = call.serviceName,
      log = "callStart $callId ${call.serviceName} ${call.functionName} ${call.args}"
    )
    return callId
  }

  override fun callEnd(call: ZiplineCall, result: Result<Any?>, callStartResult: Any?) {
    log += LogEntry(
      serviceName = call.serviceName,
      log = "callEnd $callStartResult ${call.serviceName} ${call.functionName} ${call.args} $result"
    )
  }

  override fun serviceLeaked(name: String) {
    log += LogEntry(
      serviceName = name,
      log = "serviceLeaked $name"
    )
  }

  override fun applicationLoadStart(applicationName: String, manifestUrl: String?) {
    log += LogEntry(
      applicationName = applicationName,
      log = "applicationLoadStart $applicationName $manifestUrl"
    )
  }

  override fun applicationLoadEnd(applicationName: String, manifestUrl: String?) {
    log += LogEntry(
      applicationName = applicationName,
      log = "applicationLoadEnd $applicationName $manifestUrl"
    )
  }

  override fun applicationLoadFailed(
    applicationName: String,
    manifestUrl: String?,
    exception: Exception
  ) {
    log += LogEntry(
      applicationName = applicationName,
      log = "applicationLoadFailed $applicationName $exception"
    )
  }

  override fun downloadStart(applicationName: String, url: String) {
    log += LogEntry(
      applicationName = applicationName,
      log = "downloadStart $applicationName $url"
    )
  }

  override fun downloadEnd(applicationName: String, url: String) {
    log += LogEntry(
      applicationName = applicationName,
      log = "downloadEnd $applicationName $url"
    )
  }

  override fun downloadFailed(applicationName: String, url: String, exception: Exception) {
    log += LogEntry(
      applicationName = applicationName,
      log = "downloadFailed $applicationName $url $exception"
    )
  }

  override fun manifestParseFailed(applicationName: String, url: String?, exception: Exception) {
    log += LogEntry(
      applicationName = applicationName,
      log = "manifestParseFailed $applicationName $url"
    )
  }

  fun take(
    skipServiceEvents: Boolean = false,
    skipApplicationEvents: Boolean = false,
    skipInternalServices: Boolean = true,
  ): String {
    while (true) {
      val entry = log.removeFirst()
      if (entry.matches(skipServiceEvents, skipApplicationEvents, skipInternalServices)) {
        return entry.log
      }
    }
  }

  fun takeAll(
    skipServiceEvents: Boolean = false,
    skipApplicationEvents: Boolean = false,
    skipInternalServices: Boolean = true,
  ): List<String> {
    val result = mutableListOf<String>()
    while (true) {
      val entry = log.removeFirstOrNull() ?: return result
      if (entry.matches(skipServiceEvents, skipApplicationEvents, skipInternalServices)) {
        result += entry.log
      }
    }
  }

  data class LogEntry(
    val serviceName: String? = null,
    val applicationName: String? = null,
    val log: String
  ) {
    fun matches(
      skipServiceEvents: Boolean,
      skipApplicationEvents: Boolean,
      skipInternalServices: Boolean,
    ) : Boolean {
      return (!skipServiceEvents || serviceName == null) &&
        (!skipApplicationEvents || applicationName == null) &&
        (!skipInternalServices || serviceName?.startsWith(ziplineInternalPrefix) != true)
    }
  }
}
