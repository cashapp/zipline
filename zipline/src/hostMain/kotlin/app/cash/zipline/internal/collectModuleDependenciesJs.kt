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

internal const val CURRENT_MODULE_DEPENDENCIES = "app_cash_zipline_currentModuleDependencies"

/**
 * This is a fake AMD module loader that just collects dependencies. See `defineJs.kt` for a real loader.
 * https://github.com/amdjs/amdjs-api/blob/master/AMD.md
 */
internal const val COLLECT_DEPENDENCIES_DEFINE_JS =
  """
  (function initJsModuleApi() {
    globalThis.define = function() {
      var args = Array.from(arguments);
      var factory = args.pop();
      var dependencies = (args.length > 0) ? args.pop() : [];
      var currentModuleDependencies = [];

      dependencies.map(dependency => {
        if (dependency == 'exports') {
          return {};
        } else {
          currentModuleDependencies.push(dependency);
        }
      });

      globalThis.$CURRENT_MODULE_DEPENDENCIES = JSON.stringify(currentModuleDependencies);
    };

    // By convention, we set 'define.amd' to an object to declare we confirm to the AMD spec.
    globalThis.define.amd = {};
  })();
  """
