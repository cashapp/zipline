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
#include "context-no-eval.h"

/**
 * This function is like QuickJS' JS_NewContext(...) except that it skips installing the intrinsic
 * for eval. It also omits BIGNUM which we don't include in our builds.
 *
 * We omit support for eval() as a security precaution.
 */
JSContext *JS_NewContextNoEval(JSRuntime *jsRuntime) {
  JSContext *jsContext = JS_NewContextRaw(jsRuntime);
  if (!jsContext) {
    return NULL;
  }

  JS_AddIntrinsicBaseObjects(jsContext);
  JS_AddIntrinsicDate(jsContext);
  // Eval intrinsic NOT installed here.
  JS_AddIntrinsicStringNormalize(jsContext);
  JS_AddIntrinsicRegExp(jsContext);
  JS_AddIntrinsicJSON(jsContext);
  JS_AddIntrinsicProxy(jsContext);
  JS_AddIntrinsicMapSet(jsContext);
  JS_AddIntrinsicTypedArrays(jsContext);
  JS_AddIntrinsicPromise(jsContext);
  return jsContext;
}
