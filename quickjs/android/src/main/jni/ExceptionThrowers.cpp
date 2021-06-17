/*
 * Copyright (C) 2019 Square, Inc.
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
#include "ExceptionThrowers.h"
#include "Context.h"

void throwJavaException(JNIEnv *env, const char *exceptionClass, const char *fmt, ...) {
  char msg[512];
  va_list args;
  va_start (args, fmt);
  vsnprintf(msg, sizeof(msg), fmt, args);
  va_end (args);
  env->ThrowNew(env->FindClass(exceptionClass), msg);
}

void throwJsExceptionFmt(JNIEnv *env, const Context *context, const char *fmt, ...) {
  char msg[512];
  va_list args;
  va_start (args, fmt);
  vsnprintf(msg, sizeof(msg), fmt, args);
  va_end (args);
  jobject exception = env->NewObject(context->quickJsExceptionClass,
                                     context->quickJsExceptionConstructor,
                                     env->NewStringUTF(msg),
                                     NULL);
  env->Throw(static_cast<jthrowable>(exception));
}

