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
#include <assert.h>
#include "JsCallChannel.h"
#include "quickjs/quickjs.h"
#include "Context.h"
#include "ExceptionThrowers.h"

JsCallChannel::JsCallChannel(const char *name)
    : name(name) {
}

JsCallChannel::~JsCallChannel() {
}

jobjectArray JsCallChannel::serviceNamesArray(Context *context, JNIEnv* env) const {
  JSValue global = JS_GetGlobalObject(context->jsContext);
  JSValue thisPointer = JS_GetPropertyStr(context->jsContext, global, name.c_str());
  auto property = JS_NewAtom(context->jsContext, "serviceNamesArray");

  JSValue jsResult = JS_Invoke(context->jsContext, thisPointer, property, 0, NULL);
  jobject javaResult = context->toJavaObject(env, jsResult, true);

  JS_FreeAtom(context->jsContext, property);
  JS_FreeValue(context->jsContext, jsResult);
  JS_FreeValue(context->jsContext, thisPointer);
  JS_FreeValue(context->jsContext, global);

  return static_cast<jobjectArray>(javaResult);
}

jobjectArray JsCallChannel::invoke(Context *context, JNIEnv* env, jstring instanceName,
                                   jstring funName, jobjectArray encodedArguments) const {
  JSValue global = JS_GetGlobalObject(context->jsContext);
  JSValue thisPointer = JS_GetPropertyStr(context->jsContext, global, name.c_str());
  auto property = JS_NewAtom(context->jsContext, "invoke");
  JSValueConst arguments[3];
  arguments[0] = context->toJsString(env, instanceName);
  arguments[1] = context->toJsString(env, funName);
  arguments[2] = context->toJsStringArray(env, encodedArguments);

  JSValue jsResult = JS_Invoke(context->jsContext, thisPointer, property, 3, arguments);
  jobject javaResult = context->toJavaObject(env, jsResult, true);

  JS_FreeAtom(context->jsContext, property);
  JS_FreeValue(context->jsContext, arguments[0]);
  JS_FreeValue(context->jsContext, arguments[1]);
  JS_FreeValue(context->jsContext, arguments[2]);
  JS_FreeValue(context->jsContext, jsResult);
  JS_FreeValue(context->jsContext, thisPointer);
  JS_FreeValue(context->jsContext, global);

  return static_cast<jobjectArray>(javaResult);
}

void JsCallChannel::invokeSuspending(Context *context, JNIEnv* env, jstring instanceName,
                                     jstring funName, jobjectArray encodedArguments,
                                     jstring callbackName) const {
  JSValue global = JS_GetGlobalObject(context->jsContext);
  JSValue thisPointer = JS_GetPropertyStr(context->jsContext, global, name.c_str());
  auto property = JS_NewAtom(context->jsContext, "invokeSuspending");
  JSValueConst arguments[4];
  arguments[0] = context->toJsString(env, instanceName);
  arguments[1] = context->toJsString(env, funName);
  arguments[2] = context->toJsStringArray(env, encodedArguments);
  arguments[3] = context->toJsString(env, callbackName);

  JSValue jsResult = JS_Invoke(context->jsContext, thisPointer, property, 4, arguments);
  jobject javaResult = context->toJavaObject(env, jsResult, true);

  JS_FreeAtom(context->jsContext, property);
  JS_FreeValue(context->jsContext, arguments[0]);
  JS_FreeValue(context->jsContext, arguments[1]);
  JS_FreeValue(context->jsContext, arguments[2]);
  JS_FreeValue(context->jsContext, arguments[3]);
  JS_FreeValue(context->jsContext, thisPointer);
  JS_FreeValue(context->jsContext, global);

  assert(javaResult == nullptr);
}

jobject JsCallChannel::disconnect(Context *context, JNIEnv* env, jstring instanceName) const {
  JSValue global = JS_GetGlobalObject(context->jsContext);
  JSValue thisPointer = JS_GetPropertyStr(context->jsContext, global, name.c_str());
  auto property = JS_NewAtom(context->jsContext, "disconnect");
  JSValueConst arguments[1];
  arguments[0] = context->toJsString(env, instanceName);

  JSValue jsResult = JS_Invoke(context->jsContext, thisPointer, property, 1, arguments);
  jobject javaResult = context->toJavaObject(env, jsResult, true);

  JS_FreeAtom(context->jsContext, property);
  JS_FreeValue(context->jsContext, arguments[0]);
  JS_FreeValue(context->jsContext, thisPointer);
  JS_FreeValue(context->jsContext, global);

  return javaResult;
}
