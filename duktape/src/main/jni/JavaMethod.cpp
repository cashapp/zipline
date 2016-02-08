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
#include <algorithm>
#include "JString.h"

namespace {

JavaMethod::Type getType(JNIEnv* env, jobject typeObject) {
  if (env->IsSameObject(typeObject, env->FindClass("java/lang/String"))) {
    return JavaMethod::String;
  }
  // TODO: support some more types.
  return JavaMethod::Void;
}

/**
 * Convert the duktape value at the top of the stack to the given Java type.
 * throws a JavaScript TypeError if the JS type is not {@code type}.
 */
jvalue popJavaType(JNIEnv* env, duk_context* ctx, JavaMethod::Type type) {
  const duk_idx_t index = -1;
  jvalue value;
  switch (type) {
    case JavaMethod::String:
      // Check if the caller passed in a null string.
      value.l = duk_get_type(ctx, index) != DUK_TYPE_NULL
                ? env->NewStringUTF(duk_require_string(ctx, index))
                : nullptr;
      break;

    case JavaMethod::Void:
    default:
      duk_error(ctx, DUK_ERR_API_ERROR, "unsupported argument type: %d", type);
      // unreachable - duk_error never returns.
      break;
  }
  duk_pop(ctx);
  return value;
}

/**
 * Convert the given {@code value} to a duktape type and push it onto the stack (for a function
 * call return).
 * Returns the number of entries pushed to the duktape stack.
 */
duk_ret_t pushJavaScriptType(JNIEnv* env, duk_context *ctx, JavaMethod::Type type, jvalue value) {
  switch (type) {
    case JavaMethod::Void:
      return 0;

    case JavaMethod::String: {
      const JString result(env, static_cast<jstring>(value.l));
      duk_push_string(ctx, result);
      return 1;
    }

    default:
      duk_error(ctx, DUK_ERR_API_ERROR, "unsupported return type: %d", type);
      // unreachable - duk_error never returns.
      return DUK_RET_INTERNAL_ERROR;
  }
}

} // anonymous namespace

JavaMethod::JavaMethod(JNIEnv* env, jobject method)
    : m_methodId(env->FromReflectedMethod(method)) {
  jclass methodClass = env->GetObjectClass(method);

  const jmethodID getName = env->GetMethodID(methodClass, "getName", "()Ljava/lang/String;");
  const JString methodName(env, static_cast<jstring>(env->CallObjectMethod(method, getName)));
  m_name = methodName;

  const jmethodID getReturnType =
      env->GetMethodID(methodClass, "getReturnType", "()Ljava/lang/Class;");
  jobject returnType = env->CallObjectMethod(method, getReturnType);
  m_returnType = getType(env, returnType);

  const jmethodID getParameterTypes =
      env->GetMethodID(methodClass, "getParameterTypes", "()[Ljava/lang/Class;");
  jobjectArray parameterTypes =
      static_cast<jobjectArray>(env->CallObjectMethod(method, getParameterTypes));
  const jsize numArgs = env->GetArrayLength(parameterTypes);
  m_parameterTypes.reserve(numArgs);
  for (jsize i = 0; i < numArgs; ++i) {
    m_parameterTypes.push_back(getType(env, env->GetObjectArrayElement(parameterTypes, i)));
  }
}

duk_ret_t JavaMethod::invoke(duk_context *ctx, JNIEnv *env, jobject javaThis) const {
  if (duk_get_top(ctx) != m_parameterTypes.size()) {
    duk_error(ctx, DUK_ERR_API_ERROR, "wrong number of arguments");
  }
  std::vector<jvalue> args(m_parameterTypes.size());
  std::transform(m_parameterTypes.begin(), m_parameterTypes.end(), args.rbegin(),
                 [env, ctx](Type t) { return popJavaType(env, ctx, t); });

  jvalue returnValue;
  switch (m_returnType) {
    case Void:
      returnValue.l = nullptr;
      env->CallVoidMethodA(javaThis, m_methodId, args.data());
      break;

    case String:
      returnValue.l = env->CallObjectMethodA(javaThis, m_methodId, args.data());
      break;

    default:
      return DUK_RET_INTERNAL_ERROR;
  }

  // If the Java call threw an exception, it should be propagated back through the JavaScript.
  if (env->ExceptionCheck()) {
    duk_push_error_object(ctx, DUK_ERR_API_ERROR, "Java Exception");
    duk_push_pointer(ctx, env->ExceptionOccurred());
    env->ExceptionClear();
    duk_put_prop_string(ctx, -2, JAVA_EXCEPTION_PROP_NAME);
    duk_throw(ctx);
  }

  return pushJavaScriptType(env, ctx, m_returnType, returnValue);
}
