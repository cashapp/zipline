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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Closeable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

/**
 * An EMCAScript (Javascript) interpreter backed by the 'QuickJS' native engine.
 * <p>
 * This class is NOT thread safe. If multiple threads access an instance concurrently it must be
 * externally.
 */
public final class QuickJs implements Closeable {
  static {
    QuickJsNativeLoader.load();
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
  public Object evaluate(@NonNull String script, @NonNull String fileName) {
    return evaluate(context, script, fileName);
  }

  /**
   * Evaluate {@code script} and return a result.
   *
   * @throws QuickJsException if there is an error evaluating the script.
   */
  @Nullable
  public Object evaluate(@NonNull String script) {
    return evaluate(context, script, "?");
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
  public <T> void set(@NonNull String name, @NonNull Class<T> type,
      @NonNull T object) {
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
    set(context, name, object, methods.values().toArray());
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
  @NonNull
  public <T> T get(@NonNull final String name, @NonNull final Class<T> type) {
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

    final long instance = get(context, name, methods.values().toArray());
    if (instance == 0) {
      throw new OutOfMemoryError("Cannot create QuickJs proxy to " + name);
    }

    Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
        new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // If the method is a method from Object then defer to normal invocation.
            if (method.getDeclaringClass() == Object.class) {
              return method.invoke(this, args);
            }
            return call(context, instance, method, args);
          }

          @Override
          public String toString() {
            return String.format("QuickJsProxy{name=%s, type=%s}", name, type.getName());
          }
        });
    return (T) proxy;
  }

  /**
   * Compile {@code sourceCode} and return the bytecode. {@code fileName} will be used in error
   * reporting.
   *
   * @throws QuickJsException if the sourceCode could not be compiled.
   */
  @NonNull
  public byte[] compile(@NonNull String sourceCode, @NonNull String fileName) {
    return compile(context, sourceCode, fileName);
  }

  /**
   * Load and execute {@code bytecode} and return the result.
   *
   * @throws QuickJsException if there is an error loading or executing the code.
   */
  @Nullable
  public Object execute(@NonNull byte[] bytecode) {
    return execute(context, bytecode);
  }

  /**
   * Release the native resources associated with this object. You <strong>must</strong> call this
   * method for each instance to avoid leaking native memory.
   */
  @Override public void close() {
    if (context != 0) {
      long contextToClose = context;
      context = 0;
      destroyContext(contextToClose);
    }
  }

  @Override protected void finalize() {
    if (context != 0) {
      Logger.getLogger(getClass().getName()).warning("QuickJs instance leaked!");
    }
  }

  private static native long createContext();
  private native void destroyContext(long context);
  private native Object evaluate(long context, String sourceCode, String fileName);
  private native long get(long context, String name, Object[] methods);
  private native void set(long context, String name, Object object, Object[] methods);
  private native Object call(long context, long instance, Object method, Object[] args);
  private native Object execute(long context, byte[] bytecode);
  private native byte[] compile(long context, String sourceCode, String fileName);
}
