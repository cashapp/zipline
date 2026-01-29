/*
 * Copyright (C) 2024 Block, Inc.
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
#include "global-gc.h"

static JSValue js_global_gc(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  JS_RunGC(JS_GetRuntime(ctx));
  return JS_UNDEFINED;
}

void JS_AddGlobalThisGc(JSContext *jsContext) {
  JSValue gc = JS_NewCFunction(jsContext, js_global_gc, "gc", 0);
  JSValue globalThis = JS_GetGlobalObject(jsContext);
  JS_SetPropertyStr(jsContext, globalThis, "gc", gc);
  JS_FreeValue(jsContext, globalThis);
}
