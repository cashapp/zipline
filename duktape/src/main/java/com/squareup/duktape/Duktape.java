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
package com.squareup.duktape;

import android.support.annotation.Keep;
import java.io.Closeable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** A simple EMCAScript (Javascript) interpreter. */
public final class Duktape implements Closeable {
  static {
    System.loadLibrary("duktape");
  }

  /**
   * Create a new interpreter instance. Calls to this method <strong>must</strong> matched with
   * calls to {@link #close()} on the returned instance to avoid leaking native memory.
   */
  public static Duktape create() {
    long context = createContext();
    if (context == 0) {
      throw new OutOfMemoryError("Cannot create Duktape instance");
    }
    return new Duktape(context);
  }

  private ThreadLocal<Long> context = new ThreadLocal<>();

  private Duktape(long context) {
    this.context.set(context);
  }

  /**
   * Evaluate {@code script} and return any result.  {@code fileName} will be used in error
   * reporting.
   *
   * @throws DuktapeException if there is an error evaluating the script.
   */
  public synchronized String evaluate(String script, String fileName) {
    return evaluate(getContext(), script, fileName);
  }
  /**
   * Evaluate {@code script} and return any result.
   *
   * @throws DuktapeException if there is an error evaluating the script.
   */
  public String evaluate(String script) {
    return evaluate(script, "?");
  }

  /**
   * Provides {@code object} to JavaScript as a global object called {@code name}. {@code type}
   * defines the interface implemented by {@code object} that will be accessible to JavaScript.
   * {@code type} must be an interface that does not extend any other interfaces, and cannot define
   * any overloaded methods.
   * <p>Methods of the interface may return {@code void} or any of the following supported argument
   * types: {@code boolean}, {@link Boolean}, {@code int}, {@link Integer}, {@code double},
   * {@link Double}, {@link String}.
   */
  public <T> void set(String name, Class<T> type, T object) {
    if (!type.isInterface()) {
      throw new UnsupportedOperationException("Only interfaces can be bound. Received: " + type);
    }
    if (type.getInterfaces().length > 0) {
      throw new UnsupportedOperationException(type + " must not extend other interfaces");
    }
    if (!type.isInstance(object)) {
      throw new IllegalArgumentException(object.getClass() + " is not an instance of " + type);
    }
    LinkedHashMap<String, Method> methods = new LinkedHashMap<>();
    for (Method method : type.getMethods()) {
      if (methods.put(method.getName(), method) != null) {
        throw new UnsupportedOperationException(method.getName() + " is overloaded in " + type);
      }
    }
    set(getContext(), name, object, methods.values().toArray());
  }

  /**
   * Attaches to a global JavaScript object called {@code name} that implements {@code type}.
   * {@code type} defines the interface implemented in JavaScript that will be accessible to Java.
   * {@code type} must be an interface that does not extend any other interfaces, and cannot define
   * any overloaded methods.
   * <p>Methods of the interface may return {@code void} or any of the following supported argument
   * types: {@code boolean}, {@link Boolean}, {@code int}, {@link Integer}, {@code double},
   * {@link Double}, {@link String}.
   */
  public <T> T get(final String name, final Class<T> type) {
    if (!type.isInterface()) {
      throw new UnsupportedOperationException("Only interfaces can be proxied. Received: " + type);
    }
    if (type.getInterfaces().length > 0) {
      throw new UnsupportedOperationException(type + " must not extend other interfaces");
    }
    LinkedHashMap<String, Method> methods = new LinkedHashMap<>();
    for (Method method : type.getMethods()) {
      if (methods.put(method.getName(), method) != null) {
        throw new UnsupportedOperationException(method.getName() + " is overloaded in " + type);
      }
    }

    final long instance = get(getContext(), name, methods.values().toArray());

    Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{ type },
        new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // If the method is a method from Object then defer to normal invocation.
            if (method.getDeclaringClass() == Object.class) {
              return method.invoke(this, args);
            }
            return call(getContext(), instance, method, args);
          }

          @Override
          public String toString() {
            return String.format("DuktapeProxy{name=%s, type=%s}", name, type.getName());
          }
        });
    return (T) proxy;
  }

  /**
   * Release the native resources associated with this object. You <strong>must</strong> call this
   * method for each instance to avoid leaking native memory.
   */
  @Override public synchronized void close() {
    long contextToClose = getContext();
    if (contextToClose != 0) {
      context.remove();
      destroyContext(contextToClose);
    }
  }

  @Override protected synchronized void finalize() throws Throwable {
    if (getContext() != 0) {
      Logger.getLogger(getClass().getName()).warning("Duktape instance leaked!");
    }
  }

  private long getContext() {
    Long ctx = context.get();
    return ctx != null ? ctx : 0;
  }

  private static native long createContext();
  private static native void destroyContext(long context);
  private static native String evaluate(long context, String sourceCode, String fileName);
  private static native void set(long context, String name, Object object, Object[] methods);
  private static native long get(long context, String name, Object[] methods);
  private static native Object call(long context, long instance, Object method, Object[] args);

  /** Returns the timezone offset in seconds given system time millis. */
  @SuppressWarnings("unused") // Called from native code.
  @Keep // Instruct ProGuard not to strip this method.
  private static int getLocalTimeZoneOffset(double t) {
    int offsetMillis = TimeZone.getDefault().getOffset((long) t);
    return (int) TimeUnit.MILLISECONDS.toSeconds(offsetMillis);
  }
}
