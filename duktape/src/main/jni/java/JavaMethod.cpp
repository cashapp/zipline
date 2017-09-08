/*
 * Copyright (C) 2016 Square, Inc.
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
#include "JavaMethod.h"
#include <string>
#include <stdexcept>
#include "JString.h"
#include "GlobalRef.h"
#include "JavaExceptions.h"
#include "JavaType.h"
#include "../LocalFrame.h"

JavaMethod::JavaMethod(JavaTypeMap& typeMap, JNIEnv* env, jobject method) {
  jclass methodClass = env->GetObjectClass(method);

  const jmethodID isVarArgs = env->GetMethodID(methodClass, "isVarArgs", "()Z");
  m_isVarArgs = env->CallBooleanMethod(method, isVarArgs);

  const jmethodID getParameterTypes =
      env->GetMethodID(methodClass, "getParameterTypes", "()[Ljava/lang/Class;");
  jobjectArray parameterTypes =
      static_cast<jobjectArray>(env->CallObjectMethod(method, getParameterTypes));
  const jsize numArgs = env->GetArrayLength(parameterTypes);
  // Release any local objects allocated in this frame when we leave this scope.
  const LocalFrame localFrame(env, numArgs);
  m_argumentLoaders.resize(numArgs);
  for (jsize i = 0; i < numArgs; ++i) {
    auto parameterType = env->GetObjectArrayElement(parameterTypes, i);
    if (m_isVarArgs && i == numArgs - 1) {
      const auto parameterClass = env->GetObjectClass(parameterType);
      const jmethodID getComponentType =
          env->GetMethodID(parameterClass, "getComponentType", "()Ljava/lang/Class;");
      parameterType = env->CallObjectMethod(parameterType, getComponentType);
    }
    m_argumentLoaders[i]  = typeMap.get(env, static_cast<jclass>(parameterType));
  }

  const jmethodID getReturnType =
      env->GetMethodID(methodClass, "getReturnType", "()Ljava/lang/Class;");
  jobject returnTypeObject = env->CallObjectMethod(method, getReturnType);
  const auto returnType = typeMap.get(env, static_cast<jclass>(returnTypeObject));

  const jmethodID methodId = env->FromReflectedMethod(method);
  m_methodBody = [methodId, returnType]
      (duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
    const auto result = returnType->callMethod(ctx, jniEnv, methodId, javaThis, args);
    return returnType->push(ctx, jniEnv, result);
  };
}

duk_ret_t JavaMethod::invoke(duk_context* ctx, JNIEnv* env, jobject javaThis) const {
  const auto argCount = duk_get_top(ctx);
  const auto minArgs = m_isVarArgs
      ? m_argumentLoaders.size() - 1
      : m_argumentLoaders.size();
  if (argCount < minArgs || (!m_isVarArgs && argCount > minArgs)) {
    // Wrong number of arguments given - throw an error.
    duk_error(ctx, DUK_ERR_API_ERROR, "wrong number of arguments");
    // unreachable - duk_error never returns.
    return DUK_RET_API_ERROR;
  }

  // Release any local objects allocated in this frame when we leave this scope.
  const LocalFrame localFrame(env, m_argumentLoaders.size());

  std::vector<jvalue> args(m_argumentLoaders.size());
  // Load the arguments off the stack and convert to Java types.
  // Note we're going backwards since the last argument is at the top of the stack.
  if (m_isVarArgs) {
    args.back().l = m_argumentLoaders.back()->popArray(ctx, env, argCount - minArgs, true, true);
  }
  for (ssize_t i = minArgs - 1; i >= 0; --i) {
    args[i] = m_argumentLoaders[i]->pop(ctx, env, true);
  }

  return m_methodBody(ctx, env, javaThis, args.data());
}
