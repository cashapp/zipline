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
    auto parameterType = env->GetObjectArrayElement(parameterTypes, i);
    m_argumentLoaders.push_back(getArgumentLoader(env, parameterType));
  }

  const jmethodID getReturnType =
      env->GetMethodID(methodClass, "getReturnType", "()Ljava/lang/Class;");
  jobject returnType = env->CallObjectMethod(method, getReturnType);

  const jmethodID methodId = env->FromReflectedMethod(method);
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
  // Load the arguments off the stack and convert to Java types.
  // Note we're going backwards since the last argument is at the top of the stack.
  for (ssize_t i = m_argumentLoaders.size() - 1; i >= 0; --i) {
    args[i] = m_argumentLoaders[i](ctx, env);
  }

  return m_methodBody(ctx, env, javaThis, args.data());
}

namespace {

/** Calls toString() on the given object and returns a copy of the result. */
std::string toString(JNIEnv* env, jobject object) {
  const jmethodID method =
      env->GetMethodID(env->GetObjectClass(object), "toString", "()Ljava/lang/String;");
  const JString methodName(env, static_cast<jstring>(env->CallObjectMethod(object, method)));
  return methodName.str();
}

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
 * Pops the value from the top of the stack using {@code popPrimitive}, then
 * boxes it in an object of class {@code type} by calling {@code valueOf}.
 */
jvalue boxPrimitive(duk_context* ctx, JNIEnv* env, jclass type, jmethodID valueOf,
    std::function<jvalue(duk_context*, JNIEnv*)> popPrimitive) {
  // Check if the caller passed in a null value.
  if (duk_get_type(ctx, -1) == DUK_TYPE_NULL) {
    duk_pop(ctx);
    jvalue value;
    value.l = nullptr;
    return value;
  }

  jvalue value = popPrimitive(ctx, env);
  value.l = env->CallStaticObjectMethodA(type, valueOf, &value);
  return value;
}

jvalue popBoolean(duk_context* ctx, JNIEnv*) {
  jvalue value;
  value.z = duk_require_boolean(ctx, -1);
  duk_pop(ctx);
  return value;
}

jvalue popInteger(duk_context* ctx, JNIEnv*) {
  jvalue value;
  value.i = duk_require_int(ctx, -1);
  duk_pop(ctx);
  return value;
}

jvalue popDouble(duk_context* ctx, JNIEnv*) {
  jvalue value;
  value.d = duk_require_number(ctx, -1);
  duk_pop(ctx);
  return value;
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
    return popBoolean;
  }
  if (env->IsSameObject(typeObject, env->FindClass("java/lang/Boolean"))) {
    const GlobalRef typeObjectRef(env, typeObject);
    const jmethodID valueOf = env->GetStaticMethodID(static_cast<jclass>(typeObjectRef.get()),
                                                     "valueOf",
                                                     "(Z)Ljava/lang/Boolean;");
    return [typeObjectRef, valueOf](duk_context* ctx, JNIEnv* jniEnv) {
      jclass type = static_cast<jclass>(typeObjectRef.get());
      return boxPrimitive(ctx, jniEnv, type, valueOf, popBoolean);
    };
  }

  if (env->IsSameObject(typeObject, getPrimitiveType(env, "java/lang/Integer"))) {
    return popInteger;
  }
  if (env->IsSameObject(typeObject, env->FindClass("java/lang/Integer"))) {
    const GlobalRef typeObjectRef(env, typeObject);
    const jmethodID valueOf = env->GetStaticMethodID(static_cast<jclass>(typeObjectRef.get()),
                                                     "valueOf",
                                                     "(I)Ljava/lang/Integer;");
    return [typeObjectRef, valueOf](duk_context* ctx, JNIEnv* jniEnv) {
      jclass type = static_cast<jclass>(typeObjectRef.get());
      return boxPrimitive(ctx, jniEnv, type, valueOf, popInteger);
    };
  }

  if (env->IsSameObject(typeObject, getPrimitiveType(env, "java/lang/Double"))) {
    return popDouble;
  }
  if (env->IsSameObject(typeObject, env->FindClass("java/lang/Double"))) {
    const GlobalRef typeObjectRef(env, typeObject);
    const jmethodID valueOf = env->GetStaticMethodID(static_cast<jclass>(typeObjectRef.get()),
                                                     "valueOf",
                                                     "(D)Ljava/lang/Double;");
    return [typeObjectRef, valueOf](duk_context* ctx, JNIEnv* jniEnv) {
      jclass type = static_cast<jclass>(typeObjectRef.get());
      return boxPrimitive(ctx, jniEnv, type, valueOf, popDouble);
    };
  }

  throw std::invalid_argument("Unsupported parameter type " + toString(env, typeObject));
}

duk_ret_t unboxAndPushPrimitive(duk_context* ctx, JNIEnv* jniEnv,
    jobject javaThis, jmethodID methodId, jvalue* args,
    JavaMethod::MethodBody unbox) {
  jobject returnObject = jniEnv->CallObjectMethodA(javaThis, methodId, args);
  checkRethrowDuktapeError(jniEnv, ctx);
  if (returnObject == nullptr) {
    duk_push_null(ctx);
    return 1;
  }
  return unbox(ctx, jniEnv, returnObject, nullptr);
}

/**
 * Returns a functor that will invoke the proper JNI function to get the matching {@code returnType}
 * and convert the result to a JS type then push it to the stack.  The functor returns the number
 * of entries pushed to the stack to be returned to the Duktape caller.
 */
JavaMethod::MethodBody getMethodBody(JNIEnv* env, jmethodID methodId, jobject returnType) {
  if (env->IsSameObject(returnType, env->FindClass("java/lang/String"))) {
    return [methodId](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      jobject returnValue = jniEnv->CallObjectMethodA(javaThis, methodId, args);
      checkRethrowDuktapeError(jniEnv, ctx);
      if (returnValue != nullptr) {
        const JString result(jniEnv, static_cast<jstring>(returnValue));
        duk_push_string(ctx, result);
      } else {
        duk_push_null(ctx);
      }
      return 1;
    };
  }

  if (env->IsSameObject(returnType, getPrimitiveType(env, "java/lang/Boolean"))) {
    return [methodId](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      jboolean returnValue = jniEnv->CallBooleanMethodA(javaThis, methodId, args);
      checkRethrowDuktapeError(jniEnv, ctx);
      duk_push_boolean(ctx, returnValue == JNI_TRUE);
      return 1;
    };
  }
  if (env->IsSameObject(returnType, env->FindClass("java/lang/Boolean"))) {
    jmethodID getter = env->GetMethodID(static_cast<jclass>(returnType), "booleanValue", "()Z");
    auto unbox = getMethodBody(env, getter, getPrimitiveType(env, "java/lang/Boolean"));
    return [methodId, unbox](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      return unboxAndPushPrimitive(ctx, jniEnv, javaThis, methodId, args, unbox);
    };
  }

  if (env->IsSameObject(returnType, getPrimitiveType(env, "java/lang/Integer"))) {
    return [methodId](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      jint returnValue = jniEnv->CallIntMethodA(javaThis, methodId, args);
      checkRethrowDuktapeError(jniEnv, ctx);
      duk_push_int(ctx, returnValue);
      return 1;
    };
  }
  if (env->IsSameObject(returnType, env->FindClass("java/lang/Integer"))) {
    jmethodID getter = env->GetMethodID(static_cast<jclass>(returnType), "intValue", "()I");
    auto unbox = getMethodBody(env, getter, getPrimitiveType(env, "java/lang/Integer"));
    return [methodId, unbox](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      return unboxAndPushPrimitive(ctx, jniEnv, javaThis, methodId, args, unbox);
    };
  }

  if (env->IsSameObject(returnType, getPrimitiveType(env, "java/lang/Double"))) {
    return [methodId](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      jdouble returnValue = jniEnv->CallDoubleMethodA(javaThis, methodId, args);
      checkRethrowDuktapeError(jniEnv, ctx);
      duk_push_number(ctx, returnValue);
      return 1;
    };
  }
  if (env->IsSameObject(returnType, env->FindClass("java/lang/Double"))) {
    jmethodID getter = env->GetMethodID(static_cast<jclass>(returnType), "doubleValue", "()D");
    auto unbox = getMethodBody(env, getter, getPrimitiveType(env, "java/lang/Double"));
    return [methodId, unbox](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      return unboxAndPushPrimitive(ctx, jniEnv, javaThis, methodId, args, unbox);
    };
  }

  if (env->IsSameObject(returnType, getPrimitiveType(env, "java/lang/Void"))) {
    return [methodId](duk_context* ctx, JNIEnv* jniEnv, jobject javaThis, jvalue* args) {
      jniEnv->CallVoidMethodA(javaThis, methodId, args);
      checkRethrowDuktapeError(jniEnv, ctx);
      return 0;
    };
  }

  throw std::invalid_argument("Unsupported return type " + toString(env, returnType));
}

} // anonymous namespace
