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
#include "JString.h"

namespace {

JavaMethod::ArgumentLoader getArgumentLoader(JNIEnv *env, jobject typeObject);
JavaMethod::MethodBody getMethodBody(JNIEnv* env, jmethodID methodId, jobject returnType);

} // anonymous namespace

JavaMethod::JavaMethod(JNIEnv* env, jobject method) {
  jclass methodClass = env->GetObjectClass(method);

  const jmethodID getParameterTypes =
      env->GetMethodID(methodClass, "getParameterTypes", "()[Ljava/lang/Class;");
  jobjectArray parameterTypes =
      static_cast<jobjectArray>(env->CallObjectMethod(method, getParameterTypes));
  const jsize numArgs = env->GetArrayLength(parameterTypes);
  m_argumentLoaders.reserve(numArgs);
  for (jsize i = 0; i < numArgs; ++i) {
    m_argumentLoaders.push_back(
        getArgumentLoader(env, env->GetObjectArrayElement(parameterTypes, i)));
  }

  const jmethodID getReturnType =
      env->GetMethodID(methodClass, "getReturnType", "()Ljava/lang/Class;");
  jobject returnType = env->CallObjectMethod(method, getReturnType);

  jmethodID methodId = env->FromReflectedMethod(method);
  m_methodBody = getMethodBody(env, methodId, returnType);
}

duk_ret_t JavaMethod::invoke(duk_context* ctx, JNIEnv* env, jobject javaThis) const {
  if (duk_get_top(ctx) != m_argumentLoaders.size()) {
    // Wrong number of arguments given - throw an error.
    duk_error(ctx, DUK_ERR_API_ERROR, "wrong number of arguments");
    // unreachable - duk_error never returns.
    return DUK_RET_API_ERROR;
  }

  std::vector<jvalue> args(m_argumentLoaders.size());
  // Load the arguments off the stack and convert to java types.
  // Note we're going backwards since the last argument is at the top of the stack.
  for (ssize_t i = m_argumentLoaders.size() - 1; i >= 0; --i) {
    args[i] = m_argumentLoaders[i](ctx, env);
  }

  return m_methodBody(ctx, env, javaThis, args.data());
}

namespace {

/**
 * Loads the (primitive) TYPE member of {@code boxedClassName}.
 * For example, given java/lang/Integer, this function will return int.class.
 */
jobject getPrimitiveType(JNIEnv* env, const char* boxedClassName) {
  jclass boxedClass = env->FindClass(boxedClassName);
  const jfieldID typeField = env->GetStaticFieldID(boxedClass, "TYPE", "Ljava/lang/Class;");
  return env->GetStaticObjectField(boxedClass, typeField);
}

/**
 * Get a functor that pops the object off the top of the stack and converts it to an instance of
 * {@code typeObject}.  Duktape may throw if the argument is not the correct type.
 */
JavaMethod::ArgumentLoader getArgumentLoader(JNIEnv *env, jobject typeObject) {
  if (env->IsSameObject(typeObject, env->FindClass("java/lang/String"))) {
    return [](duk_context* ctx, JNIEnv* jniEnv) {
      jvalue value;
      // Check if the caller passed in a null string.
      value.l = duk_get_type(ctx, -1) != DUK_TYPE_NULL
                ? jniEnv->NewStringUTF(duk_require_string(ctx, -1))
                : nullptr;
      duk_pop(ctx);
      return value;
    };
  }
  if (env->IsSameObject(typeObject, getPrimitiveType(env, "java/lang/Boolean"))) {
    return [](duk_context* ctx, JNIEnv*) {
      jvalue value;
      value.z = duk_require_boolean(ctx, -1);
      duk_pop(ctx);
      return value;
    };
  }
  if (env->IsSameObject(typeObject, getPrimitiveType(env, "java/lang/Integer"))) {
    return [](duk_context* ctx, JNIEnv*) {
      jvalue value;
      value.i = duk_require_int(ctx, -1);
      duk_pop(ctx);
      return value;
    };
  }
  assert(env->IsSameObject(typeObject, getPrimitiveType(env, "java/lang/Double")));
  return [](duk_context* ctx, JNIEnv*) {
    jvalue value;
    value.d = duk_require_number(ctx, -1);
    duk_pop(ctx);
    return value;
  };
}

/**
 * Determines if an exception has been thrown in this JNI thread.  If so, creates a duktape error
 * with the Java exception embedded in it, and throws it.
 */
void checkRethrowException(duk_context* ctx, JNIEnv* env) {
  if (env->ExceptionCheck()) {
    // The Java call threw an exception - it should be propagated back through JavaScript.
    duk_push_error_object(ctx, DUK_ERR_API_ERROR, "Java Exception");
    duk_push_pointer(ctx, env->ExceptionOccurred());
    env->ExceptionClear();
    duk_put_prop_string(ctx, -2, JavaMethod::JAVA_EXCEPTION_PROP_NAME);
    duk_throw(ctx);
  }
}

/**
 * Returns a functor that will invoke the proper JNI function to get the matching {@code returnType}
 * and convert the result to a JS type then push it to the stack.  The functor returns the number
 * of entries pushed to the stack to be returned to the duktape caller.
 */
JavaMethod::MethodBody getMethodBody(JNIEnv* env, jmethodID methodId, jobject returnType) {
  if (env->IsSameObject(returnType, env->FindClass("java/lang/String"))) {
    return [methodId](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      jobject returnValue = jniEnv->CallObjectMethodA(javaThis, methodId, args);
      checkRethrowException(ctx, jniEnv);
      const JString result(jniEnv, static_cast<jstring>(returnValue));
      duk_push_string(ctx, result);
      return 1;
    };
  }
  if (env->IsSameObject(returnType, getPrimitiveType(env, "java/lang/Boolean"))) {
    return [methodId](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      jboolean returnValue = jniEnv->CallBooleanMethodA(javaThis, methodId, args);
      checkRethrowException(ctx, jniEnv);
      duk_push_boolean(ctx, returnValue == JNI_TRUE);
      return 1;
    };
  }
  if (env->IsSameObject(returnType, getPrimitiveType(env, "java/lang/Integer"))) {
    return [methodId](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      jint returnValue = jniEnv->CallIntMethodA(javaThis, methodId, args);
      checkRethrowException(ctx, jniEnv);
      duk_push_int(ctx, returnValue);
      return 1;
    };
  }
  if (env->IsSameObject(returnType, getPrimitiveType(env, "java/lang/Double"))) {
    return [methodId](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      jdouble returnValue = jniEnv->CallDoubleMethodA(javaThis, methodId, args);
      checkRethrowException(ctx, jniEnv);
      duk_push_number(ctx, returnValue);
      return 1;
    };
  }
  assert(env->IsSameObject(returnType, getPrimitiveType(env, "java/lang/Void")));
  return [methodId](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
    jniEnv->CallVoidMethodA(javaThis, methodId, args);
    checkRethrowException(ctx, jniEnv);
    return 0;
  };
}

} // anonymous namespace
