/*
 * Copyright (C) 2022 Block, Inc.
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
#include <cstring>
#include <memory>
#include <assert.h>
#include "../quickjs/quickjs.h"

// This file implements a subset of the FinalizationRegistry API on QuickJS.
// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/FinalizationRegistry
//
// It works by adding a `__app_cash_zipline_finalizer` property on registered instances. This
// property exists solely to get garbage collected when its referencing object is garbage collected.
// It is an error to access this property directly.
//
// This mechanism uses a C++ function to create the finalizer and another to get called back during
// garbage collection. The rest is implemented in regular JavaScript.
//
// The Finalizer object uses an integer ID to track which JavaScript function gets called back.
// It might be simpler for the finalizer object to hold the function directly! Unfortunately that
// function either gets collected before we can execute it (if it's a regular member of the
// finalizer), or is considered a leak itself (if it's an opaque member).

namespace {

JSClassID finalizerClassId = JS_NewClassID(&finalizerClassId);

typedef struct FinalizerOpaque {
  JSContext* jsContext;
  int id;

  FinalizerOpaque(JSContext* jsContext, int id)
      : jsContext(jsContext),
        id(id) {
  }
} FinalizerOpaque;

/*
 * This is invoked by QuickJS when an instance of our special Finalizer class is garbage collected.
 * It is equivalent to the following pseudocode.
 *
 * function jsFinalizerCollected(value) {
 *   val finalizerOpaque = value.magicOpaqueValue;
 *   globalThis.app_cash_zipline_enqueueFinalizer(finalizerOpaque.id);
 * }
 *
 * Note that `magicOpaqueValue` is not a regular property; it uses JS_GetOpaque to stash a value
 * on the object.
 */
void jsFinalizerCollected(JSRuntime* jsRuntime, JSValue val) {
  const FinalizerOpaque* finalizerOpaque = reinterpret_cast<const FinalizerOpaque*>(JS_GetOpaque(val, finalizerClassId));
  auto jsContext = finalizerOpaque->jsContext;

  JSValue global = JS_GetGlobalObject(jsContext);
  const auto enqueueFinalizerName = JS_NewAtom(jsContext, "app_cash_zipline_enqueueFinalizer");

  JSValueConst arguments[1];
  arguments[0] = JS_NewInt32(jsContext, finalizerOpaque->id);

  JSValue result = JS_Invoke(jsContext, global, enqueueFinalizerName, 1, arguments);

  JS_FreeValue(jsContext, arguments[0]);
  JS_FreeAtom(jsContext, enqueueFinalizerName);
  JS_FreeValue(jsContext, global);
}

/*
 * This creates an instance of our special 'Finalizer' class, that calls jsFinalizerCollected() when
 * it is garbage collected.
 *
 * function app_cash_zipline_newFinalizer(id) {
 *   const result = new Finalizer();
 *   result.magicOpaqueValue = FinalizerOpaque(id);
 *   return result;
 * }
 */
JSValue jsNewFinalizer(JSContext* jsContext, JSValueConst this_val, int argc, JSValueConst* argv) {
  if (argc != 1) {
    return JS_ThrowSyntaxError(jsContext, "Unexpected argument count");
  }

  auto idValue = argv[0];
  int32_t id;
  if (!JS_IsNumber(idValue) || JS_ToInt32(jsContext, &id, idValue)) {
    return JS_ThrowSyntaxError(jsContext, "id is not an number");
  }

  JSValue result = JS_NewObjectClass(jsContext, finalizerClassId);
  if (JS_IsException(result)) {
    return result;
  }

  FinalizerOpaque* finalizerOpaque = new FinalizerOpaque(jsContext, id);
  JS_SetOpaque(result, finalizerOpaque);

  return result;
}

} // anonymous namespace

/*
 * This sets up the native primitives to support finalization. It's equivalent to the following
 * pseudocode.
 *
 * Declare the finalization registry class (public API) and enqueueFinalizer() method (bridges from
 * GC into user code). These are defined inline in JavaScript.
 *
 *   class FinalizationRegistry {
 *     ...
 *   }
 *
 *   function app_cash_zipline_enqueueFinalizer(id) {
 *     ...
 *   }
 *
 * Declare the finalizer class and configure the C++ function that's called when instances are
 * collected.
 *
 *   class Finalizer {
 *   }
 *
 *   Finalizer::class.setInstanceFinalizer(jsFinalizerCollected)
 *
 * Declare newFinalizer() to call the C++ factory method.
 *
 *   globalThis.app_cash_zipline_newFinalizer = jsNewFinalizer;
 *
 * Returns < 0 on failure, 1 on success.
 */
int installFinalizationRegistry(JSContext* jsContext) {
  int result = 0;
  JSRuntime* jsRuntime = JS_GetRuntime(jsContext);

  // Define the runtime API in regular JavaScript.
  auto bootstrapJs = R"(
    class FinalizationRegistry {
      static nextId = 1;
      static idToFunction = {};

      constructor(callback) {
        this.callback = callback;
      }

      register(target, heldValue) {
        const id = FinalizationRegistry.nextId++;
        FinalizationRegistry.idToFunction[id] = () => { this.callback(heldValue) };
        target.__app_cash_zipline_finalizer = app_cash_zipline_newFinalizer(id);
      }
    }

    function app_cash_zipline_enqueueFinalizer(id) {
      const f = FinalizationRegistry.idToFunction[id];
      f();
    }
  )";
  auto bootstrapResult = JS_Eval(jsContext, bootstrapJs, sizeof(bootstrapJs),
                                 "FinalizationRegistry.cpp", JS_EVAL_TYPE_GLOBAL);
  if (JS_IsException(bootstrapResult)) {
    result = -1;
  }

  // Declare the Finalizer class.
  JSClassDef classDef;
  memset(&classDef, 0, sizeof(JSClassDef));
  classDef.class_name = "Finalizer";
  classDef.finalizer = jsFinalizerCollected;
  if (JS_NewClass(jsRuntime, finalizerClassId, &classDef) < 0) {
    result = -1;
  }

  // Declare globalThis.newFinalizer().
  JSValue global = JS_GetGlobalObject(jsContext);
  const auto newFinalizerName = JS_NewAtom(jsContext, "app_cash_zipline_newFinalizer");
  JSValue newFinalizerFunction = JS_NewCFunction(jsContext, jsNewFinalizer,
                                                 "app_cash_zipline_newFinalizer", 1);
  if (JS_HasProperty(jsContext, global, newFinalizerName)
      || JS_SetProperty(jsContext, global, newFinalizerName, newFinalizerFunction) < 0) {
    result = -1;
  }
  JS_FreeAtom(jsContext, newFinalizerName);
  JS_FreeValue(jsContext, global);

  return result;
}
