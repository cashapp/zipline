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

import android.support.test.runner.AndroidJUnit4;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class DuktapeTest {
  private Duktape duktape;

  @Before public void setUp() {
    duktape = Duktape.create();
  }

  @After public void tearDown() {
    duktape.close();
  }

  @Test public void helloWorld() {
    String hello = (String) duktape.evaluate("'hello, world!'.toUpperCase();");
    assertThat(hello).isEqualTo("HELLO, WORLD!");
  }

  @Test public void evaluateReturnsNumber() {
    int result = ((Double) duktape.evaluate("2 + 3;")).intValue();
    assertThat(result).isEqualTo(5);
  }

  @Test public void exceptionsInScriptThrowInJava() {
    try {
      duktape.evaluate("nope();");
      fail();
    } catch (DuktapeException e) {
      assertThat(e).hasMessage("ReferenceError: identifier 'nope' undefined");
    }
  }

  @Test public void exceptionsInScriptIncludeStackTrace() {
    try {
      duktape.evaluate("\n"
            + "f1();\n"           // Line 2.
            + "\n"
            + "function f1() {\n"
            + "  f2();\n"         // Line 5.
            + "}\n"
            + "\n"
            + "\n"
            + "function f2() {\n"
            + "  nope();\n"       // Line 10.
            + "}\n", "test.js");
      fail();
    } catch (DuktapeException e) {
      assertThat(e).hasMessage("ReferenceError: identifier 'nope' undefined");
      assertThat(e.getStackTrace()).asList().containsAllOf(
              new StackTraceElement("JavaScript", "eval", "test.js", 2),
              new StackTraceElement("JavaScript", "f1", "test.js", 5),
              new StackTraceElement("JavaScript", "f2", "test.js", 10));
    }
  }

  @Test public void dateTimezoneOffset() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT+2:00"));
      String date = duktape.evaluate("new Date(0).toString();").toString();
      assertThat(date).isEqualTo("1970-01-01 02:00:00.000+02:00");
      int offset = ((Double) duktape.evaluate("new Date(0).getTimezoneOffset();")).intValue();
      assertThat(offset).isEqualTo(-120);
    } finally {
      TimeZone.setDefault(original);
    }
  }
}
