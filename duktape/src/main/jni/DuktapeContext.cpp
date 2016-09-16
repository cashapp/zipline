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
  throw std::runtime_error(msg);
}

} // anonymous namespace

DuktapeContext::DuktapeContext(JavaVM* javaVM)
    : m_context(duk_create_heap(nullptr, nullptr, nullptr, nullptr, fatalErrorHandler)) {
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

jstring DuktapeContext::evaluate(JNIEnv* env, jstring code, jstring fname) const {
  CHECK_STACK(m_context);
  const JString sourceCode(env, code);
  const JString fileName(env, fname);

  jstring result = nullptr;

  if (eval_string_with_filename(m_context, sourceCode, fileName) != DUK_EXEC_SUCCESS) {
    queueJavaExceptionForDuktapeError(env, m_context);
  } else {
    if (!duk_is_null_or_undefined(m_context, -1)) {
      // Return a string result (coerce the value if needed).
      result = env->NewStringUTF(duk_safe_to_string(m_context, -1));
    }
    duk_pop(m_context);
  }

  return result;
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

void DuktapeContext::loadScript(JNIEnv *env, jstring script) {
  CHECK_STACK(m_context);
  const JString sourceCode(env, script);

  duk_peval_string(m_context, sourceCode);

  duk_push_global_object(m_context);
}

void DuktapeContext::closeScriptContext() {
  CHECK_STACK(m_context);

  duk_pop(m_context);
}

void DuktapeContext::putDouble(JNIEnv *env, jstring key, jdouble value) {
  CHECK_STACK(m_context);

  JString contextKey(env, key);

  duk_push_string(m_context, contextKey);

  duk_push_number(m_context, value);

  duk_idx_t topIndex = duk_get_top_index(m_context);

  duk_put_prop(m_context, -topIndex);
}

jdouble DuktapeContext::getDouble(JNIEnv *env, jstring key) {
  CHECK_STACK(m_context);

  JString contextKey(env, key);

  if (duk_has_prop_string(m_context, -1, contextKey) != 1) {
    queueDuktapeException(env, duk_safe_to_string(m_context, -1));
    return 0L;
  } else {
    duk_get_prop_string(m_context, -1, contextKey);

    duk_double_t value = duk_is_null_or_undefined(m_context, -1) ? 0 : duk_get_number(m_context, -1);

    duk_pop(m_context);

    return value;
  }
}

void DuktapeContext::putString(JNIEnv *env, jstring key, jstring value) {
  CHECK_STACK(m_context);

  JString contextKey(env, key);

  duk_push_string(m_context, contextKey);

  if (value == nullptr) {
    duk_push_null(m_context);
  } else {
    JString contextValue(env, value);
    duk_push_string(m_context, contextValue);
  }

  duk_idx_t topIndex = duk_get_top_index(m_context);

  duk_put_prop(m_context, -topIndex);
}

jstring DuktapeContext::getString(JNIEnv *env, jstring key) {
  CHECK_STACK(m_context);

  JString contextKey(env, key);

  if (duk_has_prop_string(m_context, -1, contextKey) != 1) {
    queueDuktapeException(env, duk_safe_to_string(m_context, -1));
    return nullptr;
  } else {
    duk_get_prop_string(m_context, -1, contextKey);

    const char *value = duk_get_string(m_context, -1);

    duk_pop(m_context);

    return env->NewStringUTF(value);
  }
}

void DuktapeContext::putLong(JNIEnv *env, jstring key, jlong value) {
  CHECK_STACK(m_context);

  JString contextKey(env, key);

  duk_push_string(m_context, contextKey);

  duk_push_sprintf(m_context, "%ld", value);

  duk_idx_t topIndex = duk_get_top_index(m_context);

  duk_put_prop(m_context, -topIndex);
}

jlong DuktapeContext::getLong(JNIEnv *env, jstring key) {
  CHECK_STACK(m_context);

  JString contextKey(env, key);

  if (duk_has_prop_string(m_context, -1, contextKey) != 1) {
    queueDuktapeException(env, duk_safe_to_string(m_context, -1));
    return 0L;
  } else {
    duk_get_prop_string(m_context, -1, contextKey);
    duk_double_t value;

    if (duk_is_null_or_undefined(m_context, -1))
      value = 0;
    else {
      value = duk_is_string(m_context, -1) ?
                           atol(duk_get_string(m_context, -1)) : duk_get_number(m_context, -1);
    }

    duk_pop(m_context);

    return (jlong) value;
  }
}

void DuktapeContext::putBoolean(JNIEnv *env, jstring key, jboolean value) {
  CHECK_STACK(m_context);

  JString contextKey(env, key);

  duk_push_string(m_context, contextKey);

  duk_push_boolean(m_context, value);

  duk_idx_t topIndex = duk_get_top_index(m_context);

  duk_put_prop(m_context, -topIndex);
}

jboolean DuktapeContext::getBoolean(JNIEnv *env, jstring key) {
  CHECK_STACK(m_context);

  JString contextKey(env, key);

  if (duk_has_prop_string(m_context, -1, contextKey) != 1) {
    queueDuktapeException(env, duk_safe_to_string(m_context, -1));
    return 0L;
  } else {
    duk_get_prop_string(m_context, -1, contextKey);

    jboolean value = (jboolean) (duk_is_null_or_undefined(m_context, -1) ? JNI_FALSE : duk_get_boolean(m_context, -1));

    duk_pop(m_context);

    return value;
  }
}

jboolean DuktapeContext::isNull(JNIEnv *env, jstring key) {
  CHECK_STACK(m_context);

  JString contextKey(env, key);

  if (duk_has_prop_string(m_context, -1, contextKey) != 1) {
    queueDuktapeException(env, duk_safe_to_string(m_context, -1));
    return JNI_FALSE;
  } else {
    duk_get_prop_string(m_context, -1, contextKey);

    jboolean value = (jboolean) duk_is_null_or_undefined(m_context, -1);

    duk_pop(m_context);

    return value;
  }
}

jstring DuktapeContext::callFunction(JNIEnv *env, jstring key, jobjectArray args) {
  CHECK_STACK(m_context);

  JString contextKey(env, key);

  if (duk_has_prop_string(m_context, -1, contextKey) != 1) {
    queueDuktapeException(env, duk_safe_to_string(m_context, -1));
    return nullptr;
  } else {
    duk_get_prop_string(m_context, -1, contextKey);

    jsize size = env->GetArrayLength(args);
    
    for (jsize i = 0; i < size; i++) {
      jobject arg = env->GetObjectArrayElement(args, i);

      const JavaType *javaType = m_javaValues.get(env, env->GetObjectClass(arg));

      jvalue value;
      value.l = arg;

      javaType->push(m_context, env, value);
    }

    jstring result = nullptr;

    if (duk_pcall(m_context, size) != 0) {
      queueDuktapeException(env, duk_safe_to_string(m_context, -1));
    } else {
      if (!duk_is_null_or_undefined(m_context, -1)) {
        result = env->NewStringUTF(duk_safe_to_string(m_context, -1));
      }
    }

    duk_pop(m_context);

    return result;
  }
}
