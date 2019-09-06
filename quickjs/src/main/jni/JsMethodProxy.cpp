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

JsMethodProxy::JsMethodProxy(JNIEnv *env, const char *name, jobject method)
    : name(name), methodId(env->FromReflectedMethod(method)) {
  const jclass methodClass = env->GetObjectClass(method);

  const jmethodID getReturnType =
      env->GetMethodID(methodClass, "getReturnType", "()Ljava/lang/Class;");
  const auto returnedClass = static_cast<jclass>(env->CallObjectMethod(method, getReturnType));

  const jmethodID isVarArgsMethod = env->GetMethodID(methodClass, "isVarArgs", "()Z");
  const auto isVarArgs = env->CallBooleanMethod(method, isVarArgsMethod);

  const jmethodID getParameterTypes =
      env->GetMethodID(methodClass, "getParameterTypes", "()[Ljava/lang/Class;");
  jobjectArray parameterTypes =
      static_cast<jobjectArray>(env->CallObjectMethod(method, getParameterTypes));
  const jsize numArgs = env->GetArrayLength(parameterTypes);
  // Release any local objects allocated in this frame when we leave this scope.
  env->PushLocalFrame(numArgs);
  //argumentLoaders.resize(numArgs);
  for (jsize i = 0; i < numArgs; ++i) {
    auto parameterType = env->GetObjectArrayElement(parameterTypes, i);
    if (isVarArgs && i == numArgs - 1) {
      const auto parameterClass = env->GetObjectClass(parameterType);
      const jmethodID getComponentType =
          env->GetMethodID(parameterClass, "getComponentType", "()Ljava/lang/Class;");
      parameterType = env->CallObjectMethod(parameterType, getComponentType);
      //argumentLoaders[i] = typeMap.get(env, static_cast<jclass>(parameterType));
      break;
    }
    //  argumentLoaders[i] = typeMap.getBoxed(env, static_cast<jclass>(parameterType));
  }
  env->PopLocalFrame(nullptr);
}

jobject JsMethodProxy::call(Context *context, JSValue thisPointer, jobjectArray args) const {
  JSValue function = JS_GetPropertyStr(context->jsContext, thisPointer, name.c_str());
  const auto totalArgs = std::min<int>(argumentLoaders.size(), context->env->GetArrayLength(args));
  JSValueConst arguments[totalArgs];
  int numArgs;
  for (numArgs = 0; numArgs < totalArgs && !context->env->ExceptionCheck(); numArgs++) {
    auto arg = context->env->GetObjectArrayElement(args, numArgs);
    arguments[numArgs] = argumentLoaders[numArgs](context, arg);
  }

  jobject result;
  if (!context->env->ExceptionCheck()) {
    JSValue callResult = JS_Call(context->jsContext, function, thisPointer, numArgs, arguments);
    result = context->toJavaObject(callResult);
    JS_FreeValue(context->jsContext, callResult);
  } else {
    result = nullptr;
  }
  for (int i = 0; i < numArgs; i++) {
    JS_FreeValue(context->jsContext, arguments[i]);
  }
  JS_FreeValue(context->jsContext, function);

  return result;
}
