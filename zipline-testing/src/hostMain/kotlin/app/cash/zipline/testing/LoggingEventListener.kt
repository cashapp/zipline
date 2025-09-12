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
import app.cash.zipline.Zipline
import app.cash.zipline.ZiplineManifest
import app.cash.zipline.ZiplineService

class LoggingEventListener :
  EventListener(),
  EventListener.Factory {
  private var nextCallId = 1
  private val log = ArrayDeque<LogEntry>()

  override fun create(applicationName: String, manifestUrl: String?) = this

  override fun bindService(zipline: Zipline, name: String, service: ZiplineService) {
    log(
      service = service,
      serviceName = name,
      log = "bindService $name",
    )
  }

  override fun takeService(zipline: Zipline, name: String, service: ZiplineService) {
    log(
      service = service,
      serviceName = name,
      log = "takeService $name",
    )
  }

  override fun callStart(zipline: Zipline, call: Call): Any {
    val callId = nextCallId++
    log(
      service = call.service,
      serviceName = call.serviceName,
      log = "callStart $callId ${call.serviceName} ${call.function.signature} ${call.args}",
    )
    return callId
  }

  override fun callEnd(zipline: Zipline, call: Call, result: CallResult, startValue: Any?) {
    log(
      service = call.service,
      serviceName = call.serviceName,
      log = "callEnd $startValue " +
        "${call.serviceName} ${call.function.signature} ${call.args} ${result.result}",
    )
  }

  override fun serviceLeaked(zipline: Zipline, name: String) {
    log(
      serviceName = name,
      log = "serviceLeaked $name",
    )
  }

  override fun applicationLoadStart(applicationName: String, manifestUrl: String?) {
    log(
      applicationName = applicationName,
      log = "applicationLoadStart $applicationName $manifestUrl",
    )
  }

  override fun applicationLoadSuccess(
    applicationName: String,
    manifestUrl: String?,
    manifest: ZiplineManifest,
    zipline: Zipline,
    startValue: Any?,
  ) {
    log(
      applicationName = applicationName,
      log = "applicationLoadSuccess $applicationName $manifestUrl",
    )
  }

  override fun applicationLoadSkipped(
    applicationName: String,
    manifestUrl: String,
    startValue: Any?,
  ) {
    log(
      applicationName = applicationName,
      log = "applicationLoadSkipped $applicationName $manifestUrl",
    )
  }

  override fun applicationLoadSkippedNotFresh(
    applicationName: String,
    manifestUrl: String?,
    startValue: Any?,
  ) {
    log(
      applicationName = applicationName,
      log = "applicationLoadSkipped $applicationName $manifestUrl manifest not fresh",
    )
  }

  override fun applicationLoadFailed(
    applicationName: String,
    manifestUrl: String?,
    exception: Exception,
    startValue: Any?,
  ) {
    // On Kotlin/Native the message may include stacktrace lines. Strip those.
    val exceptionToString = exception.toString().substringBefore("\n")
    log(
      applicationName = applicationName,
      exception = exception,
      log = "applicationLoadFailed $applicationName $exceptionToString",
    )
  }

  override fun downloadStart(applicationName: String, url: String) {
    log(
      applicationName = applicationName,
      log = "downloadStart $applicationName $url",
    )
  }

  override fun downloadEnd(applicationName: String, url: String, startValue: Any?) {
    log(
      applicationName = applicationName,
      log = "downloadEnd $applicationName $url",
    )
  }

  override fun downloadFailed(
    applicationName: String,
    url: String,
    exception: Exception,
    startValue: Any?,
  ) {
    log(
      applicationName = applicationName,
      exception = exception,
      log = "downloadFailed $applicationName $url $exception",
    )
  }

  override fun cacheHit(applicationName: String, url: String, fileSize: Long) {
    log(
      applicationName = applicationName,
      log = "cacheHit $applicationName $url $fileSize",
    )
  }

  override fun manifestParseFailed(applicationName: String, url: String?, exception: Exception) {
    log(
      applicationName = applicationName,
      exception = exception,
      log = "manifestParseFailed $applicationName $url",
    )
  }

  override fun manifestVerified(
    applicationName: String,
    manifestUrl: String?,
    manifest: ZiplineManifest,
    verifiedKey: String,
  ) {
    log(
      applicationName = applicationName,
      log = "manifestVerified $applicationName $verifiedKey",
    )
  }

  override fun manifestReady(
    applicationName: String,
    manifestUrl: String?,
    manifest: ZiplineManifest,
  ) {
    log(
      applicationName = applicationName,
      log = "manifestReady $applicationName",
    )
  }

  override fun moduleLoadStart(zipline: Zipline, moduleId: String): Any? {
    log(
      moduleId = moduleId,
      log = "moduleLoadStart $moduleId",
    )
    return null
  }

  override fun moduleLoadEnd(zipline: Zipline, moduleId: String, startValue: Any?) {
    log(
      moduleId = moduleId,
      log = "moduleLoadEnd $moduleId",
    )
  }

  override fun initializerStart(zipline: Zipline, applicationName: String) {
    log(applicationName = applicationName, log = "initializerStart $applicationName")
  }

  override fun initializerEnd(zipline: Zipline, applicationName: String, startValue: Any?) {
    log(applicationName = applicationName, log = "initializerEnd $applicationName")
  }

  override fun mainFunctionStart(zipline: Zipline, applicationName: String) {
    log(applicationName = applicationName, log = "mainFunctionStart $applicationName")
  }

  override fun mainFunctionEnd(zipline: Zipline, applicationName: String, startValue: Any?) {
    log(applicationName = applicationName, log = "mainFunctionEnd $applicationName")
  }

  override fun ziplineCreated(zipline: Zipline) {
    log(log = "ziplineCreated")
  }

  override fun ziplineClosed(zipline: Zipline) {
    log(log = "ziplineClosed")
  }

  fun take(
    skipModuleEvents: Boolean = false,
    skipServiceEvents: Boolean = false,
    skipApplicationEvents: Boolean = false,
    skipInternalServices: Boolean = true,
  ): String {
    val entry = takeEntry(
      skipModuleEvents,
      skipServiceEvents,
      skipApplicationEvents,
      skipInternalServices,
    )
    return entry.log
  }

  fun takeEntry(
    skipModuleEvents: Boolean = false,
    skipServiceEvents: Boolean = false,
    skipApplicationEvents: Boolean = false,
    skipInternalServices: Boolean = true,
  ): LogEntry {
    while (true) {
      val entry = log.removeFirst()
      if (
        entry.matches(
          skipModuleEvents = skipModuleEvents,
          skipServiceEvents = skipServiceEvents,
          skipApplicationEvents = skipApplicationEvents,
          skipInternalServices = skipInternalServices,
        )
      ) {
        return entry
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
    skipModuleEvents: Boolean = false,
    skipServiceEvents: Boolean = false,
    skipApplicationEvents: Boolean = false,
    skipInternalServices: Boolean = true,
  ): List<String> {
    val result = mutableListOf<String>()
    while (true) {
      val entry = log.removeFirstOrNull() ?: return result
      if (
        entry.matches(
          skipModuleEvents = skipModuleEvents,
          skipServiceEvents = skipServiceEvents,
          skipApplicationEvents = skipApplicationEvents,
          skipInternalServices = skipInternalServices,
        )
      ) {
        result += entry.log
      }
    }
  }

  @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // Access :zipline internals.
  private fun log(
    moduleId: String? = null,
    service: ZiplineService? = null,
    serviceName: String? = null,
    applicationName: String? = null,
    exception: Exception? = null,
    log: String,
  ) {
    service.toString()
    val isInternalService = service is app.cash.zipline.internal.bridge.CancelCallback ||
      service is app.cash.zipline.internal.bridge.SuspendCallback<*> ||
      serviceName?.startsWith(app.cash.zipline.internal.ZIPLINE_INTERNAL_PREFIX) == true
    this.log += LogEntry(
      moduleId = moduleId,
      serviceToString = service?.toString(),
      serviceName = serviceName,
      applicationName = applicationName,
      isInternalService = isInternalService,
      exception = exception,
      log = log,
    )
  }

  data class LogEntry(
    val moduleId: String?,
    val serviceToString: String?,
    val serviceName: String?,
    val applicationName: String?,
    val isInternalService: Boolean,
    val exception: Exception?,
    val log: String,
  ) {
    fun matches(
      skipModuleEvents: Boolean,
      skipServiceEvents: Boolean,
      skipApplicationEvents: Boolean,
      skipInternalServices: Boolean,
    ): Boolean {
      val skip = (skipModuleEvents && moduleId != null) ||
        (skipServiceEvents && serviceName != null) ||
        (skipApplicationEvents && applicationName != null) ||
        (skipInternalServices && isInternalService)
      return !skip
    }
  }
}
