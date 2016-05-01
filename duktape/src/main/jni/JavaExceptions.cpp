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
#include "JavaExceptions.h"

namespace {

/**
 * Internal name used for storing a thrown Java exception as a property of a Duktape error object.
 * The \xff\xff part keeps the variable hidden from JavaScript (visible through C API only).
 */
const char* JAVA_EXCEPTION_PROP_NAME = "\xff\xffjava_exception";

} // anonymous namespace

void queueIllegalArgumentException(JNIEnv* env, const std::string& message) {
  const jclass illegalArgumentException = env->FindClass("java/lang/IllegalArgumentException");
  env->ThrowNew(illegalArgumentException, message.c_str());
}

void queueDuktapeException(JNIEnv* env, const std::string& message) {
  const jclass exceptionClass = env->FindClass("com/squareup/duktape/DuktapeException");
  env->ThrowNew(exceptionClass, message.c_str());
}

void queueNullPointerException(JNIEnv* env, const std::string& message) {
  jclass exceptionClass = env->FindClass("java/lang/NullPointerException");
  env->ThrowNew(exceptionClass, message.c_str());
}

void checkRethrowDuktapeError(JNIEnv* env, duk_context* ctx) {
  if (!env->ExceptionCheck()) {
    return;
  }

  // The Java call threw an exception - it should be propagated back through JavaScript.
  duk_push_error_object(ctx, DUK_ERR_API_ERROR, "Java Exception");
  duk_push_pointer(ctx, env->ExceptionOccurred());
  env->ExceptionClear();
  duk_put_prop_string(ctx, -2, JAVA_EXCEPTION_PROP_NAME);
  duk_throw(ctx);
}

void queueJavaExceptionForDuktapeError(JNIEnv *env, duk_context *ctx) {
  jclass exceptionClass = env->FindClass("com/squareup/duktape/DuktapeException");

  // If it's a Duktape error object, try to pull out the full stacktrace.
  if (duk_is_error(ctx, -1) && duk_has_prop_string(ctx, -1, "stack")) {
    duk_get_prop_string(ctx, -1, "stack");
    const char* stack = duk_safe_to_string(ctx, -1);

    // Is there an exception thrown from a Java method?
    if (duk_has_prop_string(ctx, -2, JAVA_EXCEPTION_PROP_NAME)) {
      duk_get_prop_string(ctx, -2, JAVA_EXCEPTION_PROP_NAME);
      jthrowable ex = static_cast<jthrowable>(duk_get_pointer(ctx, -1));

      // add the Duktape JavaScript stack to this exception.
      const jmethodID addDuktapeStack =
          env->GetStaticMethodID(exceptionClass,
                                 "addDuktapeStack",
                                 "(Ljava/lang/Throwable;Ljava/lang/String;)V");
      env->CallStaticVoidMethod(exceptionClass, addDuktapeStack, ex, env->NewStringUTF(stack));

      // Rethrow the Java exception.
      env->Throw(ex);

      // Pop the Java throwable.
      duk_pop(ctx);
    } else {
      env->ThrowNew(exceptionClass, stack);
    }
    // Pop the stack text.
    duk_pop(ctx);
  } else {
    // Not an error or no stacktrace, just convert to a string.
    env->ThrowNew(exceptionClass, duk_safe_to_string(ctx, -1));
  }

  duk_pop(ctx);
}
