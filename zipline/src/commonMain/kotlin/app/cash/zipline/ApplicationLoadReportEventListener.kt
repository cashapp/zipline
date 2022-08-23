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

import app.cash.zipline.internal.systemEpochMsClock
import okio.FileSystem
import okio.Path

/**
 * Generates an application load report with timing information when a Zipline application is
 * loaded. This should probably not be used in production.
 */
class ApplicationLoadReportEventListener(
  private val fileSystem: FileSystem,
  private val metricsDir: Path,
  private val nowEpochMs: () -> Long = systemEpochMsClock,
) : EventListener() {

  private val startTimes = mutableMapOf<String, Long>()
  private val applicationReports = mutableMapOf<String, ReportEntry>()

  override fun applicationLoadStart(applicationName: String, manifestUrl: String?) {
    recordStart(key(applicationName))
  }

  override fun applicationLoadEnd(applicationName: String, manifestUrl: String?, startValue: Any?) {
    val timeTaken = timeTaken(key(applicationName))

    applicationReports[applicationName]!!.line = "$timeTaken - Zipline application $applicationName loaded"
    val output = addToOutput("", applicationReports[applicationName]!!)
    fileSystem.createDirectory(metricsDir)
    fileSystem.write(metricsDir / "$applicationName.txt") {
      write(output.encodeToByteArray())
    }

    // Note this isn't safe if there are multiple Zipline instances being loaded at once
    startTimes.clear()
    applicationReports.clear()
  }

  private fun addToOutput(output: String, reportEntry: ReportEntry, nestingLevel: Int = 0): String {
    var newOutput = output
    if (output.isNotEmpty()) newOutput += "\n"
    newOutput +=" ".repeat(nestingLevel * 2)
    newOutput += reportEntry.line

    reportEntry.children.values.forEach {
      newOutput = addToOutput(newOutput, it, nestingLevel + 1)
    }
    return newOutput
  }

  override fun moduleLoadStart(
    applicationName: String,
    moduleId: String,
  ) {
    startTimes[key(applicationName, moduleId)] = nowEpochMs()
  }

  override fun moduleLoadEnd(
    applicationName: String,
    moduleId: String,
  ) {
    val key = key(applicationName, moduleId)
    addMetricEntry(key, "${timeTaken(key)} - Module $moduleId loaded")
  }

  override fun moduleUpstreamFetchStart(
    applicationName: String,
    moduleId: String,
  ) {
    startTimes[key(applicationName, moduleId, "upstream")] = nowEpochMs()
  }

  override fun moduleUpstreamFetchEnd(
    applicationName: String,
    moduleId: String,
  ) {
    val key = key(applicationName, moduleId, "upstream")
    addMetricEntry(key, "${timeTaken(key)} - Waiting on upstream modules to load")
  }

  override fun moduleReceiveStart(
    applicationName: String,
    moduleId: String,
  ) {
    recordStart(key(applicationName, moduleId, "receive"))
  }

  override fun moduleReceiveEnd(
    applicationName: String,
    moduleId: String,
  ) {
    val key = key(applicationName, moduleId, "receive")
    addMetricEntry(key, "${timeTaken(key)} - Loaded into QuickJS")
  }

  override fun moduleFetchPermitAcquireStart(
    applicationName: String,
    moduleId: String,
  ) {
    recordStart(key(applicationName, moduleId, "permit"))
  }

  override fun moduleFetchPermitAcquireEnd(
    applicationName: String,
    moduleId: String,
  ) {
    val key = key(applicationName, moduleId, "permit")
    addMetricEntry(key, "${timeTaken(key)} - Waiting to acquire fetch permit")
  }

  override fun moduleFetchStart(
    applicationName: String,
    moduleId: String,
    moduleFetcher: String,
  ) {
    recordStart(key(applicationName, moduleId, moduleFetcher))
  }

  override fun moduleFetchEnd(
    applicationName: String,
    moduleId: String,
    moduleFetcher: String,
    fetched: Boolean,
  ) {
    val key = key(applicationName, moduleId, moduleFetcher)

    val fetchedWording = if (fetched) {
      "Fetched using $moduleFetcher"
    } else {
      "Attempted to fetch using $moduleFetcher but could not resolve"
    }
    addMetricEntry(key, "${timeTaken(key)} - $fetchedWording")
  }

  private fun recordStart(key: String) = run { startTimes[key] = nowEpochMs() }

  private fun timeTaken(key: String): String {
    val time = nowEpochMs() - startTimes[key]!!
    return time.toString().padStart(4, ' ') + "ms"
  }

  private fun key(vararg parts: String) = parts.joinToString(separator = ":")

  private fun keyParts(key: String) = key.split(":")

  private fun addMetricEntry(key: String, line: String) {
    val keyParts = keyParts(key)
    var root = applicationReports[keyParts[0]]
    if (root == null) {
      root = ReportEntry("", mutableMapOf())
      applicationReports[keyParts[0]] = root
    }

    var current = root
    for (i in 1 until keyParts.size) {
      val nextKeyPart = keyParts[i]
      var next = current!!.children[nextKeyPart]
      if (next == null) {
        next = ReportEntry()
        current.children[nextKeyPart] = next
      }

      if (i == keyParts.size-1) {
        next.line = line
      }
      current = next
    }
  }
}

data class ReportEntry(
  var line: String = "",
  val children: MutableMap<String, ReportEntry> = mutableMapOf()
)
