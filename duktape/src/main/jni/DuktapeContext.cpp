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
#include "JString.h"
#include "JavaMethod.h"

namespace {

// Internal names used for properties in the duktape context's global stash and bound variables.
// The \xff\xff part keeps the variable hidden from JavaScript (visible through C API only).
const char* JAVA_VM_PROP_NAME = "\xff\xffjavaVM";
const char* JAVA_THIS_PROP_NAME = "\xff\xffjava_this";
const char* JAVA_METHOD_PROP_NAME = "\xff\xffjava_method";

JNIEnv* getJNIEnv(duk_context *ctx) {
  duk_push_global_stash(ctx);
  duk_get_prop_string(ctx, -1, JAVA_VM_PROP_NAME);
  JavaVM* javaVM = static_cast<JavaVM*>(duk_require_pointer(ctx, -1));
  duk_pop_2(ctx);

  JNIEnv* env;
  javaVM->AttachCurrentThread(&env, nullptr);
  return env;
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
  return duk_eval_raw(ctx, src, 0, DUK_COMPILE_EVAL | DUK_COMPILE_SAFE |
                                   DUK_COMPILE_NOSOURCE | DUK_COMPILE_STRLEN);
}

// Called by duktape when JS invokes a method on our bound Java object.
duk_ret_t javaMethodHandler(duk_context *ctx) {
  JavaMethod* method = getJavaMethod(ctx);
  return method != nullptr
         ? method->invoke(ctx, getJNIEnv(ctx), getJavaThis(ctx))
         : DUK_RET_INTERNAL_ERROR;
}

// Called by duktape to handle finalization of bound Java objects.
duk_ret_t javaObjectFinalizer(duk_context *ctx) {
  if (!duk_get_prop_string(ctx, -1, JAVA_THIS_PROP_NAME)) {
    return DUK_RET_INTERNAL_ERROR;
  }

  // Remove the global reference from the bound java object.
  jobject thisObject = static_cast<jobject>(duk_require_pointer(ctx, -1));
  getJNIEnv(ctx)->DeleteGlobalRef(thisObject);

  // Iterate over all of the properties, deleting all the JavaMethod objects we attached.
  duk_enum(ctx, -2, DUK_ENUM_OWN_PROPERTIES_ONLY);
  while (duk_next(ctx, -1, true)) {
    if (!duk_get_prop_string(ctx, -1, JAVA_METHOD_PROP_NAME)) {
      duk_pop_2(ctx);
      continue;
    }
    delete static_cast<JavaMethod*>(duk_require_pointer(ctx, -1));
    duk_pop_3(ctx);
  }

  duk_pop_3(ctx);
  return 0;
}

} // anonymous namespace

DuktapeContext::DuktapeContext(JavaVM* javaVM)
    : m_context(duk_create_heap_default()) {
  // Stash the JVM object in the context, so we can find our way back from a duktape C callback.
  duk_push_global_stash(m_context);
  duk_push_pointer(m_context, javaVM);
  duk_put_prop_string(m_context, -2, JAVA_VM_PROP_NAME);
  duk_pop(m_context);
}

DuktapeContext::~DuktapeContext() {
  duk_destroy_heap(m_context);
}

jstring DuktapeContext::evaluate(JNIEnv *env, jstring code, jstring fname) {
  const JString sourceCode(env, code);
  const JString fileName(env, fname);

  jstring result = nullptr;

  if (eval_string_with_filename(m_context, sourceCode, fileName) != 0) {
    jclass exceptionClass = env->FindClass("com/squareup/duktape/DuktapeException");

    // If it's a duktape error object, try to pull out the full stacktrace.
    if (duk_is_error(m_context, -1) && duk_get_prop_string(m_context, -1, "stack")) {
      const char* stack = duk_safe_to_string(m_context, -1);

      // Is there an exception thrown from a Java method?
      if (duk_get_prop_string(m_context, -2, JavaMethod::JAVA_EXCEPTION_PROP_NAME)) {
        // TODO: add the duktape stack to this exception.
        jthrowable ex = static_cast<jthrowable>(duk_get_pointer(m_context, -1));
        env->Throw(ex);
        duk_pop(m_context);
      } else {
        env->ThrowNew(exceptionClass, stack);
      }
      duk_pop(m_context);
    } else {
      // Not an error or no stacktrace, just convert to a string.
      env->ThrowNew(exceptionClass, duk_safe_to_string(m_context, -1));
    }
  } else {
    result = env->NewStringUTF(duk_get_string(m_context, -1));
  }

  duk_pop(m_context);

  return result;
}

void DuktapeContext::bindInstance(JNIEnv *env, jstring instance, jobject object,
                                  jobjectArray methods) {
  duk_push_global_object(m_context);
  const duk_idx_t objIndex = duk_require_normalize_index(m_context, duk_push_object(m_context));

  // Keep a reference in JavaScript to the object being bound.
  duk_push_pointer(m_context, env->NewGlobalRef(object));
  duk_put_prop_string(m_context, objIndex, JAVA_THIS_PROP_NAME);
  duk_push_c_function(m_context, javaObjectFinalizer, 1);
  duk_set_finalizer(m_context, objIndex);

  const jsize numMethods = env->GetArrayLength(methods);
  for (jsize i = 0; i < numMethods; ++i) {
    jobject method = env->GetObjectArrayElement(methods, i);
    JavaMethod* javaMethod = new JavaMethod(env, method);

    // Use VARARGS here to allow us to manually validate that the proper number of arguments are
    // given in the call.  If we specify the actual number of arguments needed, duktape will try to
    // be helpful by discarding extra or providing missing arguments. That's not quite what we want.
    // See http://duktape.org/api.html#duk_push_c_function for details.
    const duk_idx_t func = duk_push_c_function(m_context, javaMethodHandler, DUK_VARARGS);
    duk_push_pointer(m_context, javaMethod);
    duk_put_prop_string(m_context, func, JAVA_METHOD_PROP_NAME);

    const jmethodID getName =
        env->GetMethodID(env->GetObjectClass(method), "getName", "()Ljava/lang/String;");
    const JString methodName(env, static_cast<jstring>(env->CallObjectMethod(method, getName)));

    duk_put_prop_string(m_context, objIndex, methodName);
  }

  const JString instanceName(env, instance);
  duk_put_prop_string(m_context, -2, instanceName);

  duk_pop(m_context);
}