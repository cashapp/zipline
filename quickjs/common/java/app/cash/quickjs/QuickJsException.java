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
package app.cash.quickjs;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused") // Called from native code.
@Keep // Instruct ProGuard not to strip this type.
public final class QuickJsException extends RuntimeException {
  /**
   * QuickJs stack trace strings have multiple lines of the format "at func (file.ext:line)".
   * "func" is optional, but we'll omit frames without a function, since it means the frame is in
   * native code.
   */
  private final static Pattern STACK_TRACE_PATTERN =
      Pattern.compile("\\s*at ([^\\s]+) \\(([^\\s]+(?<!cpp))[:(\\d+)]?\\).*$");

  /** Java StackTraceElements require a class name.  We don't have one in JS, so use this. */
  private final static String STACK_TRACE_CLASS_NAME = "JavaScript";

  public QuickJsException(@NonNull String detailMessage) {
    this(detailMessage, null);
  }

  public QuickJsException(@NonNull String detailMessage, @Nullable String jsStackTrace) {
    super(detailMessage);
    if (jsStackTrace != null) addJavaScriptStack(this, jsStackTrace);
  }

  /**
   * Parses {@code StackTraceElement}s from {@code detailMessage} and adds them to the proper place
   * in {@code throwable}'s stack trace.  Note: this method is also called from native code.
   */
  private static void addJavaScriptStack(Throwable throwable, String detailMessage) {
    String[] lines = detailMessage.split("\n", -1);
    if (lines.length == 0) {
      return;
    }
    // We have a stacktrace following the message.  Add it to the exception.
    List<StackTraceElement> elements = new ArrayList<>();

    // Splice the JavaScript stack in right above the call to QuickJs.
    boolean spliced = false;
    for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
      if (!spliced
          && stackTraceElement.isNativeMethod()
          && stackTraceElement.getClassName().equals(QuickJs.class.getName())) {
        for (String line : lines) {
          StackTraceElement jsElement = toStackTraceElement(line);
          if (jsElement == null) {
            continue;
          }
          elements.add(jsElement);
        }
        spliced = true;
      }
      elements.add(stackTraceElement);
    }
    throwable.setStackTrace(elements.toArray(new StackTraceElement[elements.size()]));
  }

  private static StackTraceElement toStackTraceElement(String s) {
    Matcher m = STACK_TRACE_PATTERN.matcher(s);
    if (!m.matches()) {
      // Nothing interesting on this line.
      return null;
    }
    return new StackTraceElement(STACK_TRACE_CLASS_NAME, m.group(1), m.group(2),
        m.groupCount() > 3 ? Integer.parseInt(m.group(3)) : -1);
  }
}
