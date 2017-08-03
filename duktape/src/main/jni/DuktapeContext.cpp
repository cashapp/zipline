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
#include "DuktapeContext.h"
#include <memory>
#include <string>
#include <stdexcept>
#include <functional>
#include "java/JString.h"
#include "java/JavaMethod.h"
#include "java/GlobalRef.h"
#include "java/JavaExceptions.h"
#include "StackChecker.h"

namespace {

// Internal names used for properties in the Duktape context's global stash and bound variables.
// The \xff\xff part keeps the variable hidden from JavaScript (visible through C API only).
const char* JAVA_VM_PROP_NAME = "\xff\xffjavaVM";
const char* JAVA_THIS_PROP_NAME = "\xff\xffjava_this";
const char* JAVA_METHOD_PROP_NAME = "\xff\xffjava_method";

JNIEnv* getJNIEnv(duk_context *ctx) {
  duk_push_global_stash(ctx);
  duk_get_prop_string(ctx, -1, JAVA_VM_PROP_NAME);
  JavaVM* javaVM = static_cast<JavaVM*>(duk_require_pointer(ctx, -1));
  duk_pop_2(ctx);

  return getEnvFromJavaVM(javaVM);
}

jobject getJavaThis(duk_context* ctx) {
  duk_push_this(ctx);
  duk_get_prop_string(ctx, -1, JAVA_THIS_PROP_NAME);
  jobject thisObject = static_cast<jobject>(duk_require_pointer(ctx, -1));
  duk_pop_2(ctx);
  return thisObject;
}

JavaMethod* getJavaMethod(duk_context* ctx) {
  duk_push_current_function(ctx);
  duk_get_prop_string(ctx, -1, JAVA_METHOD_PROP_NAME);
  JavaMethod* method = static_cast<JavaMethod*>(duk_require_pointer(ctx, -1));
  duk_pop_2(ctx);
  return method;
}

duk_int_t eval_string_with_filename(duk_context *ctx, const char *src, const char *fileName) {
  duk_push_string(ctx, fileName);
  const int numArgs = 2; // source and file name
  return duk_eval_raw(ctx, src, 0, numArgs | DUK_COMPILE_EVAL | DUK_COMPILE_SAFE |
                                   DUK_COMPILE_NOSOURCE | DUK_COMPILE_STRLEN);
}

// Called by Duktape when JS invokes a method on our bound Java object.
duk_ret_t javaMethodHandler(duk_context *ctx) {
  JavaMethod* method = getJavaMethod(ctx);
  return method != nullptr
         ? method->invoke(ctx, getJNIEnv(ctx), getJavaThis(ctx))
         : DUK_RET_INTERNAL_ERROR;
}

// Called by Duktape to handle finalization of bound Java objects.
duk_ret_t javaObjectFinalizer(duk_context *ctx) {
  if (duk_get_prop_string(ctx, -1, JAVA_THIS_PROP_NAME)) {
    // Remove the global reference from the bound Java object.
    getJNIEnv(ctx)->DeleteGlobalRef(static_cast<jobject>(duk_require_pointer(ctx, -1)));
    duk_pop(ctx);
    duk_del_prop_string(ctx, -1, JAVA_METHOD_PROP_NAME);
  }

  // Iterate over all of the properties, deleting all the JavaMethod objects we attached.
  duk_enum(ctx, -1, DUK_ENUM_OWN_PROPERTIES_ONLY);
  while (duk_next(ctx, -1, true)) {
    if (!duk_get_prop_string(ctx, -1, JAVA_METHOD_PROP_NAME)) {
      duk_pop_2(ctx);
      continue;
    }
    delete static_cast<JavaMethod*>(duk_require_pointer(ctx, -1));
    duk_pop_3(ctx);
  }

  // Pop the enum and the object passed in as an argument.
  duk_pop_2(ctx);
  return 0;
}

void fatalErrorHandler(duk_context* ctx, duk_errcode_t code, const char* msg) {
#ifndef NDEBUG
  duk_push_context_dump(ctx);
  const char* debugContext = duk_get_string(ctx, -1);
  throw std::runtime_error(std::string(msg) + " (" + std::to_string(code) + ") - " + debugContext);
#else
  throw std::runtime_error(msg);
#endif
}

} // anonymous namespace

DuktapeContext::DuktapeContext(JavaVM* javaVM)
    : m_context(duk_create_heap(nullptr, nullptr, nullptr, nullptr, fatalErrorHandler))
    , m_objectType(m_javaValues.getObjectType(getEnvFromJavaVM(javaVM))) {
  if (!m_context) {
    throw std::bad_alloc();
  }

  // Stash the JVM object in the context, so we can find our way back from a Duktape C callback.
  duk_push_global_stash(m_context);
  duk_push_pointer(m_context, javaVM);
  duk_put_prop_string(m_context, -2, JAVA_VM_PROP_NAME);
  duk_pop(m_context);
}

DuktapeContext::~DuktapeContext() {
  // Delete the proxies before destroying the heap.
  m_jsObjects.clear();
  duk_destroy_heap(m_context);
}

jobject DuktapeContext::evaluate(JNIEnv* env, jstring code, jstring fname) const {
  CHECK_STACK(m_context);
  const JString sourceCode(env, code);
  const JString fileName(env, fname);

  if (eval_string_with_filename(m_context, sourceCode, fileName) != DUK_EXEC_SUCCESS) {
    queueJavaExceptionForDuktapeError(env, m_context);
    return nullptr;
  }

  const int supportedTypeMask = DUK_TYPE_MASK_BOOLEAN | DUK_TYPE_MASK_NUMBER | DUK_TYPE_MASK_STRING;
  if (duk_check_type_mask(m_context, -1, supportedTypeMask)) {
    // The result is a supported scalar type - return it.
    return m_objectType->pop(m_context, env, false).l;
  } else if (duk_is_array(m_context, -1)) {
    return m_objectType->popArray(m_context, env, 1, false, false);
  } else {
    // The result is an unsupported type, undefined, or null.
    duk_pop(m_context);
    return nullptr;
  }
}

void DuktapeContext::set(JNIEnv *env, jstring name, jobject object, jobjectArray methods) {
  CHECK_STACK(m_context);
  duk_push_global_object(m_context);
  const JString instanceName(env, name);
  if (duk_has_prop_string(m_context, -1, instanceName)) {
    duk_pop(m_context);
    queueIllegalArgumentException(env,
       "A global object called " + instanceName.str() + " already exists");
    return;
  }
  const duk_idx_t objIndex = duk_require_normalize_index(m_context, duk_push_object(m_context));

  // Hook up a finalizer to decrement the refcount and clean up our JavaMethods.
  duk_push_c_function(m_context, javaObjectFinalizer, 1);
  duk_set_finalizer(m_context, objIndex);

  const jsize numMethods = env->GetArrayLength(methods);
  for (jsize i = 0; i < numMethods; ++i) {
    jobject method = env->GetObjectArrayElement(methods, i);

    const jmethodID getName =
        env->GetMethodID(env->GetObjectClass(method), "getName", "()Ljava/lang/String;");
    const JString methodName(env, static_cast<jstring>(env->CallObjectMethod(method, getName)));

    std::unique_ptr<JavaMethod> javaMethod;
    try {
      javaMethod.reset(new JavaMethod(m_javaValues, env, method));
    } catch (const std::invalid_argument& e) {
      queueIllegalArgumentException(env, "In bound method \"" +
          instanceName.str() + "." + methodName.str() + "\": " + e.what());
      // Pop the object being bound and the duktape global object.
      duk_pop_2(m_context);
      return;
    }

    // Use VARARGS here to allow us to manually validate that the proper number of arguments are
    // given in the call.  If we specify the actual number of arguments needed, Duktape will try to
    // be helpful by discarding extra or providing missing arguments. That's not quite what we want.
    // See http://duktape.org/api.html#duk_push_c_function for details.
    const duk_idx_t func = duk_push_c_function(m_context, javaMethodHandler, DUK_VARARGS);
    duk_push_pointer(m_context, javaMethod.release());
    duk_put_prop_string(m_context, func, JAVA_METHOD_PROP_NAME);

    // Add this method to the bound object.
    duk_put_prop_string(m_context, objIndex, methodName);
  }

  // Keep a reference in JavaScript to the object being bound.
  duk_push_pointer(m_context, env->NewGlobalRef(object));
  duk_put_prop_string(m_context, objIndex, JAVA_THIS_PROP_NAME);

  // Make our bound Java object a property of the Duktape global object (so it's a JS global).
  duk_put_prop_string(m_context, -2, instanceName);
  // Pop the Duktape global object off the stack.
  duk_pop(m_context);
}

const JavaScriptObject* DuktapeContext::get(JNIEnv *env, jstring name, jobjectArray methods) {
  m_jsObjects.emplace_back(m_javaValues, env, m_context, name, methods);
  return &m_jsObjects.back();
}
