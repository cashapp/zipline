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
package app.cash.zipline.internal

internal const val CURRENT_MODULE_ID = "app_cash_zipline_currentModuleId"

/**
 * Implements an AMD module loader for Kotlin/JS AMD modules running on QuickJS.
 * https://github.com/amdjs/amdjs-api/blob/master/AMD.md
 */
internal const val DEFINE_JS =
  """
  (function initJsModuleApi() {
    // Maps module IDs (like './kotlin-kotlin-stdlib-js-ir' or 'export') to their exports.
    var idToExports = {};

    // Retrieve an exported module. This doesn't need to be a global function, but it's convenient
    // for callers who want access to a library they just loaded.
    globalThis.require = function(id) {
      var resolved = idToExports[id];
      if (!resolved) {
        throw Error('"' + id + '" not found in ' + JSON.stringify(Object.keys(idToExports)));
      }
      return resolved;
    }

    // This function accepts three arguments:
    //   id: an optional string. If absent, use the currently-loading file name.
    //   dependencies: an optional array of IDs, empty if absent.
    //   factory: user code that consumes and exports dependencies. The arguments to this function
    //      correspond 1:1 with the dependency names.
    globalThis.define = function() {
      var args = Array.from(arguments);
      var factory = args.pop();
      var dependencies = (args.length > 0) ? args.pop() : [];
      var id = (args.length > 0) ? args.pop() : globalThis.$CURRENT_MODULE_ID;
      var exports = {};

      var args = dependencies.map(dependency => {
        if (dependency == 'exports') {
          return exports;
        } else if (dependency == 'require') {
          return globalThis.require;
        } else {
          return globalThis.require(dependency);
        }
      });

      var result = factory(...args);

      idToExports[id] = result || exports;
    };

    // By convention, we set 'define.amd' to an object to declare we confirm to the AMD spec.
    globalThis.define.amd = {};
  })();
  """
