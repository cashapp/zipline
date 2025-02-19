/*
 * Copyright (C) 2025 Cash App
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
#include "ZiplineWasmInternal.h"

void throwJavaException(JNIEnv *env, const char *exceptionClass, const char *fmt, ...) {
  char msg[512];
  va_list args;
  va_start (args, fmt);
  vsnprintf(msg, sizeof(msg), fmt, args);
  va_end (args);
  env->ThrowNew(env->FindClass(exceptionClass), msg);
}

jobject createJavaWrapper(JNIEnv* env, const char* className, jlong pointer) {
    const auto resultClass = env->FindClass(className);
    const auto resultConstructor = env->GetMethodID(resultClass, "<init>", "(J)V");
    return env->NewObject(resultClass, resultConstructor, pointer);
}

jlong getPointerField(JNIEnv* env, jobject receiver) {
    // TODO: queue an IllegalStateException('closed') if the result is 0.
    const auto receiverClass = env->GetObjectClass(receiver);
    const auto pointerField = env->GetFieldID(receiverClass, "pointer", "J");
    return env->GetLongField(receiver, pointerField);
}

jlong setPointerField(JNIEnv* env, jobject receiver, jlong newValue) {
    const auto receiverClass = env->GetObjectClass(receiver);
    const auto pointerField = env->GetFieldID(receiverClass, "pointer", "J");
    const auto oldValue = env->GetLongField(receiver, pointerField);
    env->SetLongField(receiver, pointerField, newValue);
    return oldValue;
}
