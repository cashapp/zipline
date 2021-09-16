/*
 * Copyright (C) 2019 Square, Inc.
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
package com.example.duktape.octane;

import app.cash.zipline.QuickJs;
import com.squareup.duktape.Duktape;
import java.io.Closeable;

public enum Engine {
  // Keep order (and thus ordinal) in sync with strings.xml 'engines' array.
  DUKTAPE {
    @Override Closeable create() {
      return Duktape.create();
    }

    @Override Object evaluate(Object instance, String script, String fileName) {
      return ((Duktape) instance).evaluate(script, fileName);
    }
  },
  QUICK_JS {
    @Override Closeable create() {
      return QuickJs.create();
    }

    @Override Object evaluate(Object instance, String script, String fileName) {
      return ((QuickJs) instance).evaluate(script, fileName);
    }
  };

  abstract Closeable create();
  abstract Object evaluate(Object instance, String script, String fileName);
}
