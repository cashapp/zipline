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
#include "JavaMethod.h"

namespace {

// Internal names used for properties in a proxied JavaScript object.
// The \xff\xff part keeps the variable hidden from JavaScript (visible through C API only).

// We stuff JavaScriptObject pointers into an array and attach it to the proxied instance so we are
// able to detach our local reference to the object when it is garbage collected in the JS VM.
const char* WRAPPER_THIS_PROP_NAME = "\xff\xffwrapper_this";

JavaScriptObject::MethodBody buildMethodBody(JNIEnv* env, jobject method, const std::string& name);

}

JavaScriptObject::JavaScriptObject(JNIEnv* env, duk_context* context, jstring name,
                                   jobjectArray methods)
    : m_name(JString(env, name).str())
    , m_context(context)
    , m_instance(nullptr)
    , m_nextFinalizer(nullptr) {
  duk_push_global_object(m_context);
  if (!duk_get_prop_string(m_context, -1, m_name.c_str())) {
    duk_pop(m_context);
    throw std::invalid_argument("A global JavaScript object called " + m_name + " was not found");
  }

  m_instance = duk_get_heapptr(m_context, -1);
  if (m_instance == nullptr) {
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

    // Sanity check that as of right now, the object we're proxying has a function with this name.
    const JString methodName(env, static_cast<jstring>(env->CallObjectMethod(method, getName)));
    if (!duk_get_prop_string(m_context, -1, methodName)) {
      duk_pop_3(m_context);
      throw std::runtime_error("JavaScript global " + m_name + " has no method called " +
          methodName.str());
    } else if (!duk_is_callable(m_context, -1)) {
      duk_pop_3(m_context);
      throw std::runtime_error("JavaScript property " + m_name + "." + methodName.str() +
          " not callable");
    }

    try {
      // Build a call wrapper that handles marshalling the arguments and return value.
      m_methods.emplace(std::make_pair(env->FromReflectedMethod(method),
                                       buildMethodBody(env, method, methodName.str())));
    } catch (const std::invalid_argument& e) {
      duk_pop_3(m_context);
      throw std::invalid_argument("In proxied method \"" + m_name + "." + methodName.str() +
                                      "\": " + e.what());
    }

    // Pop the method property.
    duk_pop(m_context);
  }

  // Keep track of any previously registered finalizer.
  duk_get_finalizer(m_context, -1);
  m_nextFinalizer = duk_is_c_function(m_context, -1)
     ? duk_get_c_function(m_context, -1)
     : nullptr;
  duk_pop(m_context);
  duk_push_c_function(m_context, JavaScriptObject::finalizer, 1);
  duk_set_finalizer(m_context, -2);

  // Add 'this' to the list of pointers attached to the proxied instance.
  // TODO: don't use an array here, just use a separate hidden property for each proxy.
  if (!duk_has_prop_string(m_context, -1, WRAPPER_THIS_PROP_NAME)) {
    duk_push_array(m_context);
  } else {
    duk_get_prop_string(m_context, -1, WRAPPER_THIS_PROP_NAME);
  }

  const duk_size_t length = duk_get_length(m_context, -1);
  duk_push_pointer(m_context, this);
  duk_put_prop_index(m_context, -2, static_cast<duk_uarridx_t>(length));
  // Add the array (back) to the instance.
  duk_put_prop_string(m_context, -2, WRAPPER_THIS_PROP_NAME);

  // Pop the global and our instance.
  duk_pop_2(m_context);
}

JavaScriptObject::~JavaScriptObject() {
  if (!m_instance) {
    // Instance has already been cleaned up.
    return;
  }
  // The instance still exists - detach from it.
  duk_push_global_object(m_context);
  duk_push_heapptr(m_context, m_instance);

  // Remove this pointer from the JS object's property.
  if (duk_get_prop_string(m_context, -1, WRAPPER_THIS_PROP_NAME)) {
    const duk_size_t length = duk_get_length(m_context, -1);
    for (duk_uarridx_t i = 0; i < length; ++i) {
      duk_get_prop_index(m_context, -1, i);

      const void* ptr = duk_get_pointer(m_context, -1);
      duk_pop(m_context);

      if (this == ptr) {
        // Remove this object from the array.
        duk_del_prop_index(m_context, -1, i);
        break;
      }
    }
  }

  // Pop the array (or undefined if there was none).
  duk_pop(m_context);

  if (m_nextFinalizer) {
    // Reset to the object's previous finalizer.
    duk_push_c_function(m_context, m_nextFinalizer, 1);
    duk_set_finalizer(m_context, -2);
  }

  // Pop the instance & global object.
  duk_pop_2(m_context);
}

jobject JavaScriptObject::call(JNIEnv* env, jobject method, jobjectArray args) const {
  if (m_instance == nullptr) {
    queueDuktapeException(env, "JavaScript object " + m_name + " has been garbage collected");
    return nullptr;
  }

  const auto methodIter = m_methods.find(env->FromReflectedMethod(method));
  if (methodIter != m_methods.end()) {
    return methodIter->second(env, m_context, m_instance, args);
  }

  // Failed to find the method in our map - should be impossible!
  const jclass methodClass = env->GetObjectClass(method);
  const jmethodID getName = env->GetMethodID(methodClass, "getName", "()Ljava/lang/String;");
  const JString methodName(env, static_cast<jstring>(env->CallObjectMethod(method, getName)));
  queueDuktapeException(env, "Could not find method " + m_name + "." + methodName.str());
  return nullptr;
}

duk_ret_t JavaScriptObject::finalizer(duk_context* ctx) {
  // Remove this pointers from the JS object's property.
  if (duk_get_prop_string(ctx, -1, WRAPPER_THIS_PROP_NAME)) {
    const duk_size_t length = duk_get_length(ctx, -1);
    for (duk_uarridx_t i = 0; i < length; ++i) {
      duk_get_prop_index(ctx, -1, i);

      JavaScriptObject* obj = reinterpret_cast<JavaScriptObject*>(duk_get_pointer(ctx, -1));
      if (obj && obj->m_instance) {
        // Null out the instance pointer - it's been garbage collected!
        obj->m_instance = nullptr;

        if (obj->m_nextFinalizer) {
          // Continue with the next finalizer in the chain.
          obj->m_nextFinalizer(ctx);
        }
      }
      duk_pop(ctx);
    }
  }

  // Pop the array (or undefined if there was none).
  duk_pop(ctx);

  return 0;
}

namespace {

JavaScriptObject::MethodBody buildMethodBody(JNIEnv* env, jobject method,
                                             const std::string& methodName) {
  const jclass methodClass = env->GetObjectClass(method);

  const jmethodID getReturnType =
      env->GetMethodID(methodClass, "getReturnType", "()Ljava/lang/Class;");
  jobject returnType = env->CallObjectMethod(method, getReturnType);
  const auto returnValueLoader = JavaMethod::getValuePopper(env, returnType, true);

  const jmethodID getParameterTypes =
      env->GetMethodID(methodClass, "getParameterTypes", "()[Ljava/lang/Class;");
  jobjectArray parameterTypes =
      static_cast<jobjectArray>(env->CallObjectMethod(method, getParameterTypes));
  const jsize numArgs = env->GetArrayLength(parameterTypes);

  std::vector<JavaMethod::JavaValuePusher> argumentLoaders(numArgs);
  for (jsize i = 0; i < numArgs; ++i) {
    auto parameterType = env->GetObjectArrayElement(parameterTypes, i);
    argumentLoaders[i] = JavaMethod::getValuePusher(env, static_cast<jclass>(parameterType), true);
  }

  return [methodName, returnValueLoader, argumentLoaders]
      (JNIEnv* jniEnv, duk_context* ctx, void* instance, jobjectArray args) {
    duk_push_global_object(ctx);
    // Set up the call - push the object, method name, and arguments onto the stack.
    duk_push_heapptr(ctx, instance);
    duk_push_string(ctx, methodName.c_str());
    const jsize numArguments = args != nullptr ? jniEnv->GetArrayLength(args) : 0;
    jvalue arg;
    for (jsize i = 0; i < numArguments; ++i) {
      arg.l = jniEnv->GetObjectArrayElement(args, i);
      argumentLoaders[i](ctx, jniEnv, arg);
    }

    jobject result;
    if (duk_pcall_prop(ctx, -2 - numArguments, numArguments) == DUK_EXEC_SUCCESS) {
      result = returnValueLoader(ctx, jniEnv).l;
    } else {
      queueJavaExceptionForDuktapeError(jniEnv, ctx);
      result = nullptr;
    }

    // Pop the instance and global object.
    duk_pop_2(ctx);

    return result;
  };
}

} // anonymous namespace
