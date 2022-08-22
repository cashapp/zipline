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
package app.cash.zipline.samples.worldclock

import kotlin.js.Date

class TimeFormatter {
  fun formatLocalTime(
    now: dynamic = Date(),
    millis: Boolean = false,
  ): String {
    val originalHours = now.getHours()
    now.setHours(originalHours - 4) // This sample doesn't implement DST.
    val nyc = formatDate(now, millis)

    return """
      |Time in NYC
      |$nyc
      """.trimMargin()
  }

  fun formatWorldTime(
    now: dynamic = Date(),
    millis: Boolean = false,
  ): String {
    val originalHours = now.getHours()

    now.setHours(originalHours + 2)
    val barcelona = formatDate(now, millis)

    now.setHours(originalHours - 4)
    val nyc = formatDate(now, millis)

    now.setHours(originalHours - 7)
    val sf = formatDate(now, millis)

    return """
      |Barcelona
      |$barcelona
      |
      |NYC
      |$nyc
      |
      |SF
      |$sf
      """.trimMargin()
  }

  private fun formatDate(
    date: dynamic,
    millis: Boolean = false,
  ): String {
    val limit = when {
      millis -> 23
      else -> 19
    }

    val string = date.toISOString() as String
    return string.slice(11 until limit)
  }
}
