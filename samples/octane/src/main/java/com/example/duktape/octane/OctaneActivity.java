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
package com.example.duktape.octane;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import okio.BufferedSource;
import okio.Okio;

public final class OctaneActivity extends Activity {
  private Spinner engine;
  private View run;
  private TextView output;

  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.octane);

    engine = findViewById(R.id.engine);

    run = findViewById(R.id.run);
    run.setOnClickListener(v -> {
      int engineIndex = engine.getSelectedItemPosition();
      Engine engine = Engine.values()[engineIndex];
      new BenchmarkTask(engine).execute();
    });

    output = findViewById(R.id.output);
  }

  class BenchmarkTask extends AsyncTask<Void, String, Void> {
    private final Engine engine;

    BenchmarkTask(Engine engine) {
      this.engine = engine;
    }

    @Override protected void onPreExecute() {
      run.setEnabled(false);
    }

    @Override protected void onPostExecute(Void value) {
      run.setEnabled(true);
    }

    @Override protected Void doInBackground(Void... params) {
      try (Closeable instance = engine.create()) {
        for (String file : getAssets().list("octane")) {
          if (engine == Engine.DUKTAPE && file.contains("mandreel")) {
            continue; // Fails to eval. https://github.com/square/duktape-android/issues/133
          }
          evaluateAsset(instance, "octane/" + file);
        }
        evaluateAsset(instance, "octane.js");

        String results = (String) engine.evaluate(instance, "getResults();", "?");
        publishProgress("\n" + results);
      } catch (Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        publishProgress(sw.toString());
      }

      return null;
    }

    private void evaluateAsset(Object instance, String file) throws IOException {
      publishProgress(file + " eval…");

      String script;
      try (BufferedSource source = Okio.buffer(Okio.source(getAssets().open(file)))) {
        script = source.readUtf8();
      }

      long startNanos = System.nanoTime();
      engine.evaluate(instance, script, file);
      long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

      publishProgress(" " + tookMs + " ms\n");
    }

    private final StringBuilder outputText = new StringBuilder();

    @Override protected void onProgressUpdate(String... values) {
      outputText.append(values[0]);
      output.setText(outputText.toString());
    }
  }
}
