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

#include "JavaScriptObject.h"
#include <stdexcept>
#include "JString.h"
#include "JavaExceptions.h"

// TODO: debug instance tracking issues so we don't need to lookup by name on a call.
#define NO_INSTANCE_VAR

namespace {

// Internal names used for properties in a proxied JavaScript object.
// The \xff\xff part keeps the variable hidden from JavaScript (visible through C API only).
const char* WRAPPER_THIS_PROP_NAME = "\xff\xffwrapper_this";

}

JavaScriptObject::JavaScriptObject(JNIEnv* env, duk_context* context, const std::string& name,
                                   jobjectArray methods)
    : m_name(name)
    , m_context(context)
    , m_instance(nullptr)
    , m_nextFinalizer(nullptr) {
  duk_push_global_object(m_context);
  if (!duk_get_prop_string(m_context, -1, m_name.c_str())) {
    duk_pop(m_context);
    throw std::invalid_argument("A global JavaScript object called " + m_name + " was not found");
  }

#ifdef NO_INSTANCE_VAR
  if (!duk_is_object(m_context, -1)) {
#else
  m_instance = duk_get_heapptr(m_context, -1);
  if (m_instance == nullptr) {
#endif
    duk_pop_2(m_context);
    throw std::invalid_argument("JavaScript global called " + m_name + " is not an object");
  }

  // Make sure that the object has all of the methods we want.
  jmethodID getName = nullptr;
  const jsize numArgs = env->GetArrayLength(methods);
  for (jsize i = 0; i < numArgs; ++i) {
    auto method = env->GetObjectArrayElement(methods, i);
    if (getName == nullptr) {
      jclass methodClass = env->GetObjectClass(method);
      getName = env->GetMethodID(methodClass, "getName", "()Ljava/lang/String;");
    }

    const JString methodName(env, static_cast<jstring>(env->CallObjectMethod(method, getName)));
    if (!duk_get_prop_string(m_context, -1, methodName)) {
      duk_pop_2(m_context);
      throw std::runtime_error("JavaScript global " + m_name + " has no method called " +
          methodName.str());
    } else if (!duk_is_callable(m_context, -1)) {
      duk_pop_3(m_context);
      throw std::runtime_error("JavaScript property " + m_name + "." + methodName.str() +
          " not callable");
    }

    // TODO: build a call wrapper that handles marshalling the arguments and return value.
    //       Store it in this object.

    // Pop the method property.
    duk_pop(m_context);
  }

#ifndef NO_INSTANCE_VAR
  // Keep track of any previously registered finalizer.
  duk_get_finalizer(m_context, -1);
  m_nextFinalizer = duk_is_c_function(m_context, -1)
     ? duk_get_c_function(m_context, -1)
     : nullptr;
  duk_pop(m_context);
  duk_push_c_function(m_context, JavaScriptObject::finalizer, 1);
  duk_set_finalizer(m_context, -2);
#endif

  duk_push_pointer(m_context, this);
  duk_put_prop_string(m_context, -2, WRAPPER_THIS_PROP_NAME);

  // Pop the global and our instance.
  duk_pop_2(m_context);
}

JavaScriptObject::~JavaScriptObject() {
  if (m_instance) {
    // The instance still exists - detach from it.
    duk_push_heapptr(m_context, m_instance);
    duk_del_prop_string(m_context, -1, WRAPPER_THIS_PROP_NAME);

    // Reset to the object's previous finalizer.
    duk_push_c_function(m_context, m_nextFinalizer, 1);
    duk_set_finalizer(m_context, -2);
    duk_pop(m_context);
  }
}

jobject JavaScriptObject::call(JNIEnv* env, jobject method, jobjectArray args) {
#ifdef NO_INSTANCE_VAR
  duk_push_global_object(m_context);
  if (!duk_get_prop_string(m_context, -1, m_name.c_str())) {
    duk_pop(m_context);
    queueNullPointerException(env, "JavaScript object called " + m_name + " was not found");
    return nullptr;
  }
#else
  if (m_instance == nullptr) {
    queueNullPointerException(env, "JavaScript object " + m_name + " has been garbage collected");
    return nullptr;
  }
  duk_push_heapptr(m_context, m_instance);
#endif

  jclass methodClass = env->GetObjectClass(method);

  const jmethodID getName = env->GetMethodID(methodClass, "getName", "()Ljava/lang/String;");
  const JString methodName(env, static_cast<jstring>(env->CallObjectMethod(method, getName)));
  duk_push_string(m_context, methodName);

  // TODO: put the arguments (from args) on the stack too.

  jobject result;
  if (duk_pcall_prop(m_context, -2, 0) == DUK_EXEC_SUCCESS) {
    // TODO: handle other return types.
    result = duk_is_null_or_undefined(m_context, -1)
        ? nullptr
        : env->NewStringUTF(duk_safe_to_string(m_context, -1));
  } else {
    queueJavaExceptionForDuktapeError(env, m_context);
    result = nullptr;
  }

  // Pop our instance and the call's result or error.
  duk_pop_3(m_context);

  return result;
}

duk_ret_t JavaScriptObject::finalizer(duk_context* ctx) {
  if (!duk_get_prop_string(ctx, -1, WRAPPER_THIS_PROP_NAME)) {
    return 0;
  }

  JavaScriptObject* obj = reinterpret_cast<JavaScriptObject*>(duk_require_pointer(ctx, -1));
  duk_pop(ctx);

  duk_del_prop_string(ctx, -1, WRAPPER_THIS_PROP_NAME);

  // Null out the instance pointer - it's been garbage collected!
  obj->m_instance = nullptr;

  if (obj->m_nextFinalizer) {
    // Continue with the next finalizer in the chain.
    obj->m_nextFinalizer(ctx);
  }
  return 0;
}