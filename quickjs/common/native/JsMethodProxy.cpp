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

#include "JsMethodProxy.h"
#include <algorithm>
#include "Context.h"

JsMethodProxy::JsMethodProxy(Context* context, JNIEnv* env, const char* name, jobject method)
    : name(name), methodId(env->FromReflectedMethod(method)) {
  const jclass methodClass = env->GetObjectClass(method);

  const jmethodID getReturnType =
      env->GetMethodID(methodClass, "getReturnType", "()Ljava/lang/Class;");
  const auto returnedClass = static_cast<jclass>(env->CallObjectMethod(method, getReturnType));
  resultLoader = context->getJsToJavaConverter(env, returnedClass, true);
  env->DeleteLocalRef(returnedClass);
  if (!env->ExceptionCheck()) {
    const jmethodID isVarArgsMethod = env->GetMethodID(methodClass, "isVarArgs", "()Z");
    isVarArgs = env->CallBooleanMethod(method, isVarArgsMethod);

    const jmethodID getParameterTypes =
        env->GetMethodID(methodClass, "getParameterTypes", "()[Ljava/lang/Class;");
    jobjectArray parameterTypes =
        static_cast<jobjectArray>(env->CallObjectMethod(method, getParameterTypes));
    const jsize numArgs = env->GetArrayLength(parameterTypes);
    for (jsize i = 0; i < numArgs && !env->ExceptionCheck(); ++i) {
      auto parameterType = env->GetObjectArrayElement(parameterTypes, i);
      argumentLoaders.push_back(
          context->getJavaToJsConverter(env, static_cast<jclass>(parameterType), true));
      env->DeleteLocalRef(parameterType);
    }
    env->DeleteLocalRef(parameterTypes);
  }
  env->DeleteLocalRef(methodClass);
}

jobject JsMethodProxy::call(Context* context, JNIEnv* env, JSValue thisPointer, jobjectArray args) const {
  const auto totalArgs = std::min<int>(argumentLoaders.size(), args ? env->GetArrayLength(args) : 0);
  std::vector<JSValue> arguments;
  int numArgs;
  jvalue arg;
  for (numArgs = 0; numArgs < totalArgs && !env->ExceptionCheck(); numArgs++) {
    arg.l = env->GetObjectArrayElement(args, numArgs);
    if (!isVarArgs || numArgs < totalArgs - 1) {
      arguments.push_back(argumentLoaders[numArgs](context, env, arg));
    } else {
      auto varArgs = argumentLoaders[numArgs](context, env, arg);
      if (JS_IsArray(context->jsContext, varArgs)) {
        auto len = JS_GetPropertyStr(context->jsContext, varArgs, "length");
        for (int i = 0, e = JS_VALUE_GET_INT(len); i < e; i++) {
          arguments.push_back(JS_GetPropertyUint32(context->jsContext, varArgs, i));
        }
        JS_FreeValue(context->jsContext, len);
        JS_FreeValue(context->jsContext, varArgs);
      } else {
        arguments.push_back(varArgs);
      }
    }
    env->DeleteLocalRef(arg.l);
  }

  jobject result;
  if (!env->ExceptionCheck()) {
    auto property = JS_NewAtom(context->jsContext, name.c_str());
    JSValue callResult = JS_Invoke(context->jsContext, thisPointer, property, arguments.size(),
                                   arguments.data());
    JS_FreeAtom(context->jsContext, property);
    result = resultLoader(context, env, callResult).l;
    JS_FreeValue(context->jsContext, callResult);
  } else {
    result = nullptr;
  }
  for (JSValue argument : arguments) {
    JS_FreeValue(context->jsContext, argument);
  }

  return result;
}
