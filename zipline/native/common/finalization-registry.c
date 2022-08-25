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
#include "../quickjs/quickjs.h"
#include "finalization-registry.h"
#include <stdlib.h>
#include <string.h>

// This file implements a subset of the FinalizationRegistry API on QuickJS.
// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/FinalizationRegistry
//
// It works by adding a `__app_cash_zipline_finalizer` property on registered instances. This
// property exists solely to get garbage collected when its referencing object is garbage collected.
// It is an error to access this property directly.
//
// This mechanism uses a C function to create the finalizer and another to get called back during
// garbage collection. The rest is implemented in regular JavaScript.
//
// The Finalizer object uses an integer ID to track which JavaScript function gets called back.
// It might be simpler for the finalizer object to hold the function directly! Unfortunately that
// function either gets collected before we can execute it (if it's a regular member of the
// finalizer), or is considered a leak itself (if it's an opaque member).

JSClassID finalizerClassId = 0;

typedef struct FinalizerOpaque {
  JSContext* jsContext;
  int id;
} FinalizerOpaque;

/*
 * This is invoked by QuickJS when an instance of our special Finalizer class is garbage collected.
 * It is equivalent to the following pseudocode.
 *
 * function jsFinalizerCollected(value) {
 *   const finalizerOpaque = value.magicOpaqueValue;
 *   globalThis.app_cash_zipline_enqueueFinalizer(finalizerOpaque.id);
 * }
 *
 * Note that `magicOpaqueValue` is not a regular property; it uses JS_GetOpaque to stash a value
 * on the object.
 */
static void jsFinalizerCollected(JSRuntime* jsRuntime, JSValue val) {
  FinalizerOpaque* finalizerOpaque = (FinalizerOpaque*)JS_GetOpaque(val, finalizerClassId);
  JSContext* jsContext = finalizerOpaque->jsContext;
  JSValue global = JS_GetGlobalObject(jsContext);

  // Don't dereference the global object if QuickJS is shutting down.
  if (JS_IsLiveObject(jsRuntime, global)) {
    const JSAtom enqueueFinalizerName = JS_NewAtom(jsContext, "app_cash_zipline_enqueueFinalizer");

    JSValueConst arguments[1];
    arguments[0] = JS_NewInt32(jsContext, finalizerOpaque->id);

    JS_Invoke(jsContext, global, enqueueFinalizerName, 1, arguments);

    JS_SetOpaque(val, NULL);
    JS_FreeValue(jsContext, arguments[0]);
    JS_FreeAtom(jsContext, enqueueFinalizerName);
  }

  free(finalizerOpaque);
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
static JSValue jsNewFinalizer(JSContext* jsContext, JSValueConst this_val, int argc, JSValueConst* argv) {
  if (argc != 1) {
    return JS_ThrowSyntaxError(jsContext, "Unexpected argument count");
  }

  JSValueConst idValue = argv[0];
  int32_t id;
  if (!JS_IsNumber(idValue) || JS_ToInt32(jsContext, &id, idValue)) {
    return JS_ThrowSyntaxError(jsContext, "id is not an number");
  }

  JSValue result = JS_NewObjectClass(jsContext, finalizerClassId);
  if (JS_IsException(result)) {
    return result;
  }

  FinalizerOpaque* finalizerOpaque = malloc(sizeof(FinalizerOpaque));
  finalizerOpaque->jsContext = jsContext;
  finalizerOpaque->id = id;
  JS_SetOpaque(result, finalizerOpaque);

  return result;
}

/**
 * Compiles and executes [bootstrapJs] using one JSContext to compile and another to execute. We
 * would normally just use JS_Eval but we've disabled eval on that JSContext as a security
 * precaution.
 *
 * In order to compile with one JSContext and run on another, we do an encode/decode cycle on the
 * intermediate function. That's a simple (if inefficient) way to move a function across contexts.
 *
 * Returns 1 on success, -1 on error.
 */
static int compileAndExecuteJs(JSContext *jsContext, JSContext *jsContextForCompiling, char *sourceCode) {
  int result = 1;

  JSValue compiledFunction = JS_Eval(jsContextForCompiling, sourceCode, strlen(sourceCode),
                                     "finalization-registry.c",
                                     JS_EVAL_FLAG_COMPILE_ONLY | JS_EVAL_FLAG_STRICT);

  if (JS_IsException(compiledFunction)) {
    result = -1;
  } else {
    size_t encodedFunctionLength = 0;
    uint8_t *encodedFunction = JS_WriteObject(jsContextForCompiling, &encodedFunctionLength, compiledFunction,
                                              JS_WRITE_OBJ_BYTECODE | JS_WRITE_OBJ_REFERENCE);
    JSValue runnableFunction = JS_ReadObject(jsContext, encodedFunction, encodedFunctionLength,
                                             JS_READ_OBJ_BYTECODE | JS_READ_OBJ_REFERENCE);
    js_free(jsContextForCompiling, encodedFunction);

    if (JS_IsException(runnableFunction) || JS_ResolveModule(jsContext, runnableFunction)) {
      result = -1;
    } else {
      JSValue bootstrapResult = JS_EvalFunction(jsContext, runnableFunction);
      if (JS_IsException(bootstrapResult)) {
        result = -1;
      }
      JS_FreeValue(jsContext, bootstrapResult);
    }
  }

  JS_FreeValue(jsContextForCompiling, compiledFunction);

  return result;
}

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
 * Declare the finalizer class and configure the C function that's called when instances are
 * collected.
 *
 *   class Finalizer {
 *   }
 *
 *   Finalizer::class.setInstanceFinalizer(jsFinalizerCollected)
 *
 * Declare newFinalizer() to call the C factory method.
 *
 *   globalThis.app_cash_zipline_newFinalizer = jsNewFinalizer;
 *
 * Returns < 0 on failure, 1 on success.
 */
int installFinalizationRegistry(JSContext *jsContext, JSContext *jsContextForCompiling) {
  int result = 1;
  JSRuntime* jsRuntime = JS_GetRuntime(jsContext);

  if (finalizerClassId == 0) {
    JS_NewClassID(&finalizerClassId);
  }

  // Define the runtime API in regular JavaScript.
  char* bootstrapJs =
    "class FinalizationRegistry {\n"
    "  static nextId = 1;\n"
    "  static idToFunction = {};\n"
    "\n"
    "  constructor(callback) {\n"
    "    this.callback = callback;\n"
    "  }\n"
    "\n"
    "  register(target, heldValue) {\n"
    "    const id = FinalizationRegistry.nextId++;\n"
    "    FinalizationRegistry.idToFunction[id] = () => { this.callback(heldValue) };\n"
    "    target.__app_cash_zipline_finalizer = app_cash_zipline_newFinalizer(id);\n"
    "  }\n"
    "}\n"
    "\n"
    "function app_cash_zipline_enqueueFinalizer(id) {\n"
    "  const f = FinalizationRegistry.idToFunction[id];\n"
    "  f();\n"
    "}\n";
  if (compileAndExecuteJs(jsContext, jsContextForCompiling, bootstrapJs) < 0) {
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

  // Declare globalThis.app_cash_zipline_newFinalizer().
  JSValue global = JS_GetGlobalObject(jsContext);
  const JSAtom newFinalizerName = JS_NewAtom(jsContext, "app_cash_zipline_newFinalizer");
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
