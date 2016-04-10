/*
 * Copyright (C) 2016 Square, Inc.
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class DuktapeProxyTest {
  private Duktape duktape;

  @Before public void setUp() {
    duktape = Duktape.create();
  }

  @After public void tearDown() {
    duktape.close();
  }

  @Test public void proxyNonInterface() {
    try {
      duktape.proxy("s", String.class);
      fail();
    } catch (UnsupportedOperationException expected) {
      assertThat(expected)
          .hasMessage("Only interfaces can be proxied. Received: class java.lang.String");
    }
  }

  interface TestInterface {
    String getValue();
  }

  @Test public void proxy() {
    duktape.evaluate("var value = { getValue: function() { return '8675309'; } };");
    TestInterface proxy = duktape.proxy("value", TestInterface.class);
    String v = proxy.getValue();
    assertThat(v).isEqualTo("8675309");
  }

  @Test public void proxyMissingObjectThrows() {
    TestInterface proxy = duktape.proxy("DoesNotExist", TestInterface.class);
    assertThat(proxy).isNotNull();
    try {
      proxy.getValue();
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("A global object called DoesNotExist was not found");
    }
  }

  @Test public void proxyMissingMethodThrows() {
    duktape.evaluate("var value = { getOtherValue: function() { return '8675309'; } };");
    TestInterface proxy = duktape.proxy("value", TestInterface.class);
    try {
      proxy.getValue();
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected).hasMessage("TypeError: undefined not callable");
    }
  }

  @Test public void proxyMethodNotCallableThrows() {
    duktape.evaluate("var value = { getValue: '8675309' };");
    TestInterface proxy = duktape.proxy("value", TestInterface.class);
    try {
      proxy.getValue();
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected).hasMessage("TypeError: '8675309' not callable");
    }
  }

  @Test public void proxyCalledAfterDuktapeClosed() {
    duktape.evaluate("var value = { getValue: function() { return '8675309'; } };");
    TestInterface proxy = duktape.proxy("value", TestInterface.class);

    // Close the context - proxy can no longer be used.
    duktape.close();

    try {
      proxy.getValue();
      fail();
    } catch (NullPointerException expected) {
      assertThat(expected).hasMessage("Null Duktape context - did you close your Duktape?");
    }
  }

  @Test public void proxyCallThrows() {
    duktape.evaluate("var value = { getValue: function() { throw 'nope'; } };");
    TestInterface proxy = duktape.proxy("value", TestInterface.class);

    try {
      proxy.getValue();
      fail();
    } catch (DuktapeException expected) {
      assertThat(expected).hasMessage("nope");
    }
  }
}
