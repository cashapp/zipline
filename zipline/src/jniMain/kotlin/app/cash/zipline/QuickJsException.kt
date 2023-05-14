/*
 * Copyright (C) 2015 Square, Inc.
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

import androidx.annotation.Keep
import java.util.regex.Pattern

@Keep // Instruct ProGuard not to strip this type.
actual class QuickJsException @JvmOverloads constructor(
  detailMessage: String,
  jsStackTrace: String? = null,
) : RuntimeException(detailMessage) {
  init {
    if (jsStackTrace != null) {
      addJavaScriptStack(jsStackTrace)
    }
  }

  private companion object {
    /**
     * QuickJs stack trace strings have multiple lines of the format "at func (file.ext:line)".
     * "func" is optional, but we'll omit frames without a function, since it means the frame is in
     * native code.
     */
    private val STACK_TRACE_PATTERN =
      Pattern.compile("\\s*at ([^\\s]+) \\(([^\\s]+(?<!cpp))[:(\\d+)]?\\).*$")

    /** Java StackTraceElements require a class name.  We don't have one in JS, so use this.  */
    private const val STACK_TRACE_CLASS_NAME = "JavaScript"

    /**
     * Parses `StackTraceElement`s from `detailMessage` and adds them to the proper place
     * in `throwable`'s stack trace.
     *
     * NOTE: This method is also called from native code.
     */
    @JvmStatic // Expose for easy invocation from native.
    @JvmSynthetic // Hide from public API to Java consumers.
    fun Throwable.addJavaScriptStack(detailMessage: String) {
      val lines = detailMessage.split('\n').dropLastWhile(String::isEmpty)
      if (lines.isEmpty()) {
        return
      }
      // We have a stacktrace following the message. Add it to the exception.
      val elements = mutableListOf<StackTraceElement>()

      // Splice the JavaScript stack in right above the call to QuickJs.
      var spliced = false
      for (stackTraceElement in stackTrace) {
        if (!spliced &&
          stackTraceElement.isNativeMethod &&
          stackTraceElement.isZipline
        ) {
          spliced = true
          for (line in lines) {
            val jsElement = toStackTraceElement(line) ?: continue
            elements += jsElement
          }
        }
        elements += stackTraceElement
      }
      stackTrace = elements.toTypedArray()
    }

    private val StackTraceElement.isZipline: Boolean
      get() = className == QuickJs::class.java.name || className == JniCallChannel::class.java.name

    private fun toStackTraceElement(s: String): StackTraceElement? {
      val m = STACK_TRACE_PATTERN.matcher(s)
      return if (!m.matches()) {
        null // Nothing interesting on this line.
      } else {
        StackTraceElement(
          STACK_TRACE_CLASS_NAME,
          m.group(1),
          m.group(2),
            if (m.groupCount() > 3) m.group(3)!!.toInt() else -1,
        )
      }
    }
  }
}
