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
#include "OutboundCallChannel.h"
#include "Context.h"
#include "ExceptionThrowers.h"

OutboundCallChannel::OutboundCallChannel(Context* c, JNIEnv* env, const char* name, jobject object,
                                         JSValueConst jsOutboundCallChannel)
    : context(c),
      name(name),
      javaThis(env->NewGlobalRef(object)),
      callChannelClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("app/cash/zipline/internal/bridge/CallChannel")))),
      callMethod(env->GetMethodID(callChannelClass, "call", "(Ljava/lang/String;)Ljava/lang/String;")),
      disconnectMethod(env->GetMethodID(callChannelClass, "disconnect", "(Ljava/lang/String;)Z")) {
  functions.push_back(JS_CFUNC_DEF("call", 1, OutboundCallChannel::call));
  functions.push_back(JS_CFUNC_DEF("disconnect", 1, OutboundCallChannel::disconnect));
  if (!env->ExceptionCheck()) {
    JS_SetPropertyFunctionList(context->jsContext, jsOutboundCallChannel, functions.data(), functions.size());
  }
}

OutboundCallChannel::~OutboundCallChannel() {
  context->getEnv()->DeleteGlobalRef(javaThis);
  context->getEnv()->DeleteGlobalRef(callChannelClass);
}

JSValue
OutboundCallChannel::call(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv) {
  auto context = reinterpret_cast<const Context*>(JS_GetRuntimeOpaque(JS_GetRuntime(ctx)));
  if (!context) {
    return JS_ThrowReferenceError(ctx, "QuickJs closed");
  }
  auto channel = reinterpret_cast<const OutboundCallChannel*>(JS_GetOpaque(this_val, context->outboundCallChannelClassId));
  if (!channel) {
    return JS_ThrowReferenceError(ctx, "Not an OutboundCallChannel");
  }

  assert(argc == 1);

  auto env = context->getEnv();
  env->PushLocalFrame(argc + 1);
  jvalue args[1];
  args[0].l = context->toJavaString(env, argv[0]);

  jstring javaResult = static_cast<jstring>(env->CallObjectMethodA(
      channel->javaThis, channel->callMethod, args));
  JSValue jsResult;
  if (!env->ExceptionCheck()) {
    jsResult = context->toJsString(env, javaResult);
  } else {
    jsResult = context->throwJavaExceptionFromJs(env);
  }
  env->PopLocalFrame(nullptr);
  return jsResult;
}

JSValue
OutboundCallChannel::disconnect(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv) {
  auto context = reinterpret_cast<const Context*>(JS_GetRuntimeOpaque(JS_GetRuntime(ctx)));
  if (!context) {
    return JS_ThrowReferenceError(ctx, "QuickJs closed");
  }
  auto channel = reinterpret_cast<const OutboundCallChannel*>(JS_GetOpaque(this_val, context->outboundCallChannelClassId));
  if (!channel) {
    return JS_ThrowReferenceError(ctx, "Not an OutboundCallChannel");
  }

  assert(argc == 1);

  auto env = context->getEnv();
  env->PushLocalFrame(argc + 1);
  jvalue args[1];
  args[0].l = context->toJavaString(env, argv[0]);

  jboolean javaResult = env->CallBooleanMethodA(
      channel->javaThis, channel->disconnectMethod, args);
  JSValue jsResult;
  if (!env->ExceptionCheck()) {
    jsResult = JS_NewBool(context->jsContext, javaResult);
  } else {
    jsResult = context->throwJavaExceptionFromJs(env);
  }
  env->PopLocalFrame(nullptr);
  return jsResult;
}
