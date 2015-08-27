
package com.squareup.duktape;

import java.io.Closeable;
import java.util.logging.Logger;

/**
 * To execute on your Mac,
 *
 *   1. run build.sh
 *   2. run with the java.library.path set to <project-root>/duktape/src/main/jni
 *      For example -Djava.library.path=/Users/jwilson/Square/duktape-android/duktape/src/main/jni/
 */
public class Duktape implements Closeable {
  static {
    System.loadLibrary("duktape");
  }

  private long context;

  public Duktape() {
    this.context = createContext();
    if (this.context == 0) {
      throw new OutOfMemoryError("Cannot create Duktape");
    }
  }

  public static void main(String[] args) {
    for (int i = 0; i < 1000000; i++) {
      example();
    }
  }

  public static void example() {
    Duktape duktape = new Duktape();
    try {
      System.out.println(duktape.evaluate("var a = function(b) { return b.toUpperCase();  };"));
      System.out.println(duktape.evaluate("a('hello world');"));
    } catch (Exception e) {
      System.err.print(e);
    } finally {
      duktape.close();
    }
  }

  public synchronized String evaluate(String s) {
    return evaluate(context, s);
  }

  @Override
  public synchronized void close() {
    if (context != 0) {
      long contextToClose = context;
      context = 0;
      destroyContext(contextToClose);
    }
  }

  @Override
  protected synchronized void finalize() throws Throwable {
    if (context != 0) {
      Logger.getLogger(getClass().getName()).warning("Duktape instance leaked!");
    }
  }

  private static native long createContext();
  private static native void destroyContext(long context);
  private static native String evaluate(long context, String s);
}
