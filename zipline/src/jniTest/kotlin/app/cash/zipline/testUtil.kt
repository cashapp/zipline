/*
 * Copyright (C) 2021 Square, Inc.
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

import java.io.BufferedReader
import kotlin.reflect.KClass
import org.intellij.lang.annotations.Language

// TODO
// create arraylist of the files
// load them all progressively into quickjs

// may need to look at how require works, rewrite as an assertion or recursive loader
// quickjs doesn't handle require natively so we need to write our own
// define export and require such that require blows up if code with matching export hasn't been loaded yet
// load code bottom up

fun Zipline.loadTestingJs() {
  // may need to add support for id set to null
  @Language("Javascript")
  val script = """
    var loadedModuleExports = {};

    globalThis.require = function(id) {
      return loadedModuleExports[id];
    }

    globalThis.define = function() {
      var args = Array.from(arguments);
      var factory = args.pop();
      var dependencies = (args.length == 0) ? [] : args.pop();
      var id = (args.length == 0) ? null : args.pop();
      var exports = {};

      var args = dependencies.map(dependency => {
        if (dependency == 'exports') {
          return exports;
        }
        if (dependency == 'require') {
          return require;
        }

        var resolved = loadedModuleExports[dependency];
        if (!resolved) {
          throw Error("unable to resolve " + dependency + " for module '" + id + "'");
        }

        return resolved;
      });

      var loaded = factory(...args);

      if (id == null) {
        id = globalThis.currentFileName
      }
      loadedModuleExports[id] = exports;
    };

    globalThis.define.amd = {};
    """.trimIndent()
  quickJs.evaluate(
    script, "require-export.js"
  )

  // TODO add a manifest to the JS directory that includes this information as an ordered by load list
  val files = listOf(
    "kotlin-kotlin-stdlib-js-ir.js",
    "88b0986a7186d029-atomicfu-js-ir.js",
    "kotlinx-serialization-kotlinx-serialization-core-js-ir.js",
    "kotlinx-serialization-kotlinx-serialization-json-js-ir.js",
    "kotlinx.coroutines-kotlinx-coroutines-core-js-ir.js",
    "zipline-root-zipline.js",
    "zipline-root-testing.js",
  )

  files.forEach { fileName ->
    quickJs.evaluate("""
      globalThis.currentFileName = "./$fileName"
    """.trimIndent(), "filename-setter.js")
    val fileJs = Zipline::class.java.getResourceAsStream("/$fileName")!!
      .bufferedReader()
      .use(BufferedReader::readText)
    quickJs.evaluate(fileJs, fileName)
  }

  quickJs.evaluate("""
    globalThis['testing'] = require('./zipline-root-testing.js');
  """.trimIndent(), "symlink.js")
}

operator fun <T : Any> QuickJs.set(name: String, type: KClass<T>, instance: T) {
  error("no longer supported")
}

operator fun <T : Any> QuickJs.get(name: String, type: KClass<T>): T {
  error("no longer supported")
}
