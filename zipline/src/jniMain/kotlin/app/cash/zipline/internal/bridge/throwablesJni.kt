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
package app.cash.zipline.internal.bridge

/** When encoding a stacktrace, chop Zipline frames off below the inbound call. */
internal actual fun stacktraceString(throwable: Throwable): String {
  for ((index, element) in throwable.stackTrace.withIndex()) {
    if (element.className.startsWith(Endpoint::class.qualifiedName!!)) {
      throwable.stackTrace = throwable.stackTrace.sliceArray(0 until index)
      break
    }
  }

  return throwable.stackTraceToString().trim()
}

/** When decoding a stacktrace, chop Zipline frames off above the outbound call. */
internal actual fun toInboundThrowable(
  stacktraceString: String,
  constructor: (String) -> Throwable,
): Throwable {
  // Strip empty lines and format 'at' to match java.lang.Throwable.
  val canonicalString = stacktraceString
    .replace(Regex("\n[ ]+at "), "\n\tat ")
    .replace(Regex("\n+"), "\n")
    .trim()
  val result = constructor(canonicalString)

  val stackTrace = result.stackTrace
  for (i in stackTrace.size - 1 downTo 0) {
    if (stackTrace[i].className == OutboundCallHandler::class.qualifiedName) {
      result.stackTrace = stackTrace.sliceArray(i + 1 until stackTrace.size)
      break
    }
  }

  return result
}
