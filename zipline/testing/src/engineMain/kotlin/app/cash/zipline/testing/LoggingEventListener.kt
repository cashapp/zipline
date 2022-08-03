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

import app.cash.zipline.Call
import app.cash.zipline.CallResult
import app.cash.zipline.EventListener
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.bridge.CancelCallback
import app.cash.zipline.internal.bridge.SuspendCallback
import app.cash.zipline.internal.ziplineInternalPrefix

class LoggingEventListener : EventListener() {
  private var nextCallId = 1
  private val log = ArrayDeque<LogEntry>()

  override fun bindService(name: String, service: ZiplineService) {
    log(
      service = service,
      serviceName = name,
      log = "bindService $name"
    )
  }

  override fun takeService(name: String, service: ZiplineService) {
    log(
      service = service,
      serviceName = name,
      log = "takeService $name"
    )
  }

  override fun callStart(call: Call): Any? {
    val callId = nextCallId++
    log(
      service = call.service,
      serviceName = call.serviceName,
      log = "callStart $callId ${call.serviceName} ${call.function.name} ${call.args}"
    )
    return callId
  }

  override fun callEnd(call: Call, result: CallResult, callStartResult: Any?) {
    log(
      service = call.service,
      serviceName = call.serviceName,
      log = "callEnd $callStartResult " +
        "${call.serviceName} ${call.function.name} ${call.args} ${result.result}"
    )
  }

  override fun serviceLeaked(name: String) {
    log(
      serviceName = name,
      log = "serviceLeaked $name"
    )
  }

  override fun applicationLoadStart(applicationName: String, manifestUrl: String?) {
    log(
      applicationName = applicationName,
      log = "applicationLoadStart $applicationName $manifestUrl"
    )
  }

  override fun applicationLoadEnd(applicationName: String, manifestUrl: String?) {
    log(
      applicationName = applicationName,
      log = "applicationLoadEnd $applicationName $manifestUrl"
    )
  }

  override fun applicationLoadFailed(
    applicationName: String,
    manifestUrl: String?,
    exception: Exception
  ) {
    log(
      applicationName = applicationName,
      exception = exception,
      log = "applicationLoadFailed $applicationName $exception"
    )
  }

  override fun downloadStart(applicationName: String, url: String) {
    log(
      applicationName = applicationName,
      log = "downloadStart $applicationName $url"
    )
  }

  override fun downloadEnd(applicationName: String, url: String) {
    log(
      applicationName = applicationName,
      log = "downloadEnd $applicationName $url"
    )
  }

  override fun downloadFailed(applicationName: String, url: String, exception: Exception) {
    log(
      applicationName = applicationName,
      exception = exception,
      log = "downloadFailed $applicationName $url $exception"
    )
  }

  override fun manifestParseFailed(applicationName: String, url: String?, exception: Exception) {
    log(
      applicationName = applicationName,
      exception = exception,
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

  fun takeException(): Exception {
    while (true) {
      val entry = log.removeFirst()
      if (entry.exception != null) {
        return entry.exception
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

  private fun log(
    service: ZiplineService? = null,
    serviceName: String? = null,
    applicationName: String? = null,
    exception: Exception? = null,
    log: String,
  ) {
    val isInternalService = service is CancelCallback ||
      service is SuspendCallback<*> ||
      serviceName?.startsWith(ziplineInternalPrefix) == true
    this.log += LogEntry(serviceName, applicationName, isInternalService, exception, log)
  }

  data class LogEntry(
    val serviceName: String?,
    val applicationName: String?,
    val isInternalService: Boolean,
    val exception: Exception?,
    val log: String,
  ) {
    fun matches(
      skipServiceEvents: Boolean,
      skipApplicationEvents: Boolean,
      skipInternalServices: Boolean,
    ) : Boolean {
      val skip = (skipServiceEvents && serviceName != null)
        || (skipApplicationEvents && applicationName != null)
        || (skipInternalServices && isInternalService)
      return !skip
    }
  }
}
