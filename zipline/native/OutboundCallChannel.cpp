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
      serviceNamesArrayMethod(env->GetMethodID(callChannelClass, "serviceNamesArray", "()[Ljava/lang/String;")),
      invokeMethod(env->GetMethodID(callChannelClass, "invoke", "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;")),
      invokeSuspendingMethod(env->GetMethodID(callChannelClass, "invokeSuspending", "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)V")),
      disconnectMethod(env->GetMethodID(callChannelClass, "disconnect", "(Ljava/lang/String;)Z")) {
  functions.push_back(JS_CFUNC_DEF("serviceNamesArray", 0, OutboundCallChannel::serviceNamesArray));
  functions.push_back(JS_CFUNC_DEF("invoke", 3, OutboundCallChannel::invoke));
  functions.push_back(JS_CFUNC_DEF("invokeSuspending", 4, OutboundCallChannel::invokeSuspending));
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
OutboundCallChannel::serviceNamesArray(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv) {
  auto context = reinterpret_cast<const Context*>(JS_GetRuntimeOpaque(JS_GetRuntime(ctx)));
  if (!context) {
    return JS_ThrowReferenceError(ctx, "QuickJs closed");
  }
  auto channel = reinterpret_cast<const OutboundCallChannel*>(JS_GetOpaque(this_val, context->jsClassId));
  if (!channel) {
    return JS_ThrowReferenceError(ctx, "Not an OutboundCallChannel");
  }

  auto env = context->getEnv();
  env->PushLocalFrame(argc + 1);
  jvalue args[0];
  jobjectArray javaResult = static_cast<jobjectArray>(env->CallObjectMethodA(
      channel->javaThis, channel->serviceNamesArrayMethod, args));
  JSValue jsResult;
  if (!env->ExceptionCheck()) {
    jsResult = context->toJsStringArray(env, javaResult);
  } else {
    jsResult = context->throwJavaExceptionFromJs(env);
  }
  env->PopLocalFrame(nullptr);
  return jsResult;
}

JSValue
OutboundCallChannel::invoke(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv) {
  auto context = reinterpret_cast<const Context*>(JS_GetRuntimeOpaque(JS_GetRuntime(ctx)));
  if (!context) {
    return JS_ThrowReferenceError(ctx, "QuickJs closed");
  }
  auto channel = reinterpret_cast<const OutboundCallChannel*>(JS_GetOpaque(this_val, context->jsClassId));
  if (!channel) {
    return JS_ThrowReferenceError(ctx, "Not an OutboundCallChannel");
  }

  assert(argc == 3);

  auto env = context->getEnv();
  env->PushLocalFrame(argc + 1);
  jvalue args[3];
  args[0].l = context->toJavaString(env, argv[0]);
  args[1].l = context->toJavaString(env, argv[1]);
  args[2].l = context->toJavaStringArray(env, argv[2]);

  jobjectArray javaResult = static_cast<jobjectArray>(env->CallObjectMethodA(
      channel->javaThis, channel->invokeMethod, args));
  JSValue jsResult;
  if (!env->ExceptionCheck()) {
    jsResult = context->toJsStringArray(env, javaResult);
  } else {
    jsResult = context->throwJavaExceptionFromJs(env);
  }
  env->PopLocalFrame(nullptr);
  return jsResult;
}

JSValue
OutboundCallChannel::invokeSuspending(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv) {
  auto context = reinterpret_cast<const Context*>(JS_GetRuntimeOpaque(JS_GetRuntime(ctx)));
  if (!context) {
    return JS_ThrowReferenceError(ctx, "QuickJs closed");
  }
  auto channel = reinterpret_cast<const OutboundCallChannel*>(JS_GetOpaque(this_val, context->jsClassId));
  if (!channel) {
    return JS_ThrowReferenceError(ctx, "Not an OutboundCallChannel");
  }

  assert(argc == 4);

  auto env = context->getEnv();
  env->PushLocalFrame(argc + 1);
  jvalue args[4];
  args[0].l = context->toJavaString(env, argv[0]);
  args[1].l = context->toJavaString(env, argv[1]);
  args[2].l = context->toJavaStringArray(env, argv[2]);
  args[3].l = context->toJavaString(env, argv[3]);

  env->CallVoidMethodA(channel->javaThis, channel->invokeSuspendingMethod, args);
  JSValue jsResult;
  if (!env->ExceptionCheck()) {
    jsResult = JS_UNDEFINED;
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
  auto channel = reinterpret_cast<const OutboundCallChannel*>(JS_GetOpaque(this_val, context->jsClassId));
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
