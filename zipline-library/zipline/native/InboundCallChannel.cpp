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
#include "InboundCallChannel.h"
#include "quickjs/quickjs.h"
#include "Context.h"
#include "ExceptionThrowers.h"

InboundCallChannel::InboundCallChannel(JSContext *jsContext, const char *name)
    : jsContext(jsContext),
      nameAtom(JS_NewAtom(jsContext, name)){
}

InboundCallChannel::~InboundCallChannel() {
  JS_FreeAtom(jsContext, nameAtom);
}

jobjectArray InboundCallChannel::serviceNamesArray(Context *context, JNIEnv* env) const {
  JSContext *jsContext = context->jsContext;
  JSValue global = JS_GetGlobalObject(jsContext);
  JSValue thisPointer = JS_GetProperty(jsContext, global, nameAtom);

  JSValue jsResult = JS_Invoke(jsContext, thisPointer, context->serviceNamesArrayAtom, 0, NULL);
  jobjectArray javaResult;
  auto tag = JS_VALUE_GET_NORM_TAG(jsResult);
  if (tag == JS_TAG_EXCEPTION) {
    context->throwJsException(env, jsResult);
    javaResult = nullptr;
  } else if (tag == JS_TAG_OBJECT) {
    javaResult = context->toJavaStringArray(env, jsResult);
  } else {
    assert(false); // Unexpected tag.
  }

  JS_FreeValue(jsContext, jsResult);
  JS_FreeValue(jsContext, thisPointer);
  JS_FreeValue(jsContext, global);

  return javaResult;
}

jobjectArray InboundCallChannel::invoke(Context *context, JNIEnv* env, jstring instanceName,
                                   jstring funName, jobjectArray encodedArguments) const {
  JSContext *jsContext = context->jsContext;
  JSValue global = JS_GetGlobalObject(jsContext);
  JSValue thisPointer = JS_GetProperty(jsContext, global, nameAtom);
  JSValueConst arguments[3];
  arguments[0] = context->toJsString(env, instanceName);
  arguments[1] = context->toJsString(env, funName);
  arguments[2] = context->toJsStringArray(env, encodedArguments);

  JSValue jsResult = JS_Invoke(jsContext, thisPointer, context->invokeAtom, 3, arguments);
  jobjectArray javaResult;
  auto tag = JS_VALUE_GET_NORM_TAG(jsResult);
  if (tag == JS_TAG_EXCEPTION) {
    context->throwJsException(env, jsResult);
    javaResult = nullptr;
  } else if (tag == JS_TAG_OBJECT) {
    javaResult = context->toJavaStringArray(env, jsResult);
  } else {
    assert(false); // Unexpected tag.
  }

  JS_FreeValue(jsContext, arguments[0]);
  JS_FreeValue(jsContext, arguments[1]);
  JS_FreeValue(jsContext, arguments[2]);
  JS_FreeValue(jsContext, jsResult);
  JS_FreeValue(jsContext, thisPointer);
  JS_FreeValue(jsContext, global);

  return javaResult;
}

void InboundCallChannel::invokeSuspending(Context *context, JNIEnv* env, jstring instanceName,
                                     jstring funName, jobjectArray encodedArguments,
                                     jstring callbackName) const {
  JSContext *jsContext = context->jsContext;
  JSValue global = JS_GetGlobalObject(jsContext);
  JSValue thisPointer = JS_GetProperty(jsContext, global, nameAtom);
  JSValueConst arguments[4];
  arguments[0] = context->toJsString(env, instanceName);
  arguments[1] = context->toJsString(env, funName);
  arguments[2] = context->toJsStringArray(env, encodedArguments);
  arguments[3] = context->toJsString(env, callbackName);

  JSValue jsResult = JS_Invoke(jsContext, thisPointer, context->invokeSuspendingAtom, 4, arguments);
  auto tag = JS_VALUE_GET_NORM_TAG(jsResult);
  if (tag == JS_TAG_EXCEPTION) {
    context->throwJsException(env, jsResult);
  } else if (tag == JS_TAG_UNDEFINED) {
    // Expected. Do nothing.
  } else {
    assert(false); // Unexpected tag.
  }

  JS_FreeValue(jsContext, arguments[0]);
  JS_FreeValue(jsContext, arguments[1]);
  JS_FreeValue(jsContext, arguments[2]);
  JS_FreeValue(jsContext, arguments[3]);
  JS_FreeValue(jsContext, thisPointer);
  JS_FreeValue(jsContext, global);
}

jboolean InboundCallChannel::disconnect(Context *context, JNIEnv* env, jstring instanceName) const {
  JSContext *jsContext = context->jsContext;
  JSValue global = JS_GetGlobalObject(jsContext);
  JSValue thisPointer = JS_GetProperty(jsContext, global, nameAtom);
  JSValueConst arguments[1];
  arguments[0] = context->toJsString(env, instanceName);

  JSValue jsResult = JS_Invoke(jsContext, thisPointer, context->disconnectAtom, 1, arguments);
  jboolean javaResult;
  auto tag = JS_VALUE_GET_NORM_TAG(jsResult);
  if (tag == JS_TAG_EXCEPTION) {
    context->throwJsException(env, jsResult);
    javaResult = JNI_FALSE;
  } else if (tag == JS_TAG_BOOL) {
    javaResult = static_cast<jboolean>(JS_VALUE_GET_BOOL(jsResult));
  } else {
    assert(false); // Unexpected tag.
  }

  JS_FreeValue(jsContext, arguments[0]);
  JS_FreeValue(jsContext, thisPointer);
  JS_FreeValue(jsContext, global);

  return javaResult;
}
