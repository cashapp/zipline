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
package com.squareup.quickjs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Closeable;
import java.util.logging.Logger;

/** An EMCAScript (Javascript) interpreter backed by the 'QuickJS' native engine. */
public final class QuickJs implements Closeable {
  static {
    System.loadLibrary("quickjs");
  }

  /**
   * Create a new interpreter instance. Calls to this method <strong>must</strong> matched with
   * calls to {@link #close()} on the returned instance to avoid leaking native memory.
   */
  @NonNull
  public static QuickJs create() {
    long context = createContext();
    if (context == 0) {
      throw new OutOfMemoryError("Cannot create QuickJs instance");
    }
    return new QuickJs(context);
  }

  private long context;

  private QuickJs(long context) {
    this.context = context;
  }

  /**
   * Evaluate {@code script} and return any result. {@code fileName} will be used in error
   * reporting.
   *
   * @throws QuickJsException if there is an error evaluating the script.
   */
  @Nullable
  public synchronized Object evaluate(@NonNull String script, @NonNull String fileName) {
    return evaluate(context, script, fileName);
  }

  /**
   * Evaluate {@code script} and return a result.
   *
   * @throws QuickJsException if there is an error evaluating the script.
   */
  @Nullable
  public synchronized Object evaluate(@NonNull String script) {
    return evaluate(context, script, "?");
  }

  /**
   * Release the native resources associated with this object. You <strong>must</strong> call this
   * method for each instance to avoid leaking native memory.
   */
  @Override public synchronized void close() {
    if (context != 0) {
      long contextToClose = context;
      context = 0;
      destroyContext(contextToClose);
    }
  }

  @Override protected synchronized void finalize() {
    if (context != 0) {
      Logger.getLogger(getClass().getName()).warning("QuickJs instance leaked!");
    }
  }

  private static native long createContext();
  private native void destroyContext(long context);
  private native Object evaluate(long context, String sourceCode, String fileName);
}
