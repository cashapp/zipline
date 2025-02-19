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
#ifndef ZIPLINE_WASM_INTERNAL_H
#define ZIPLINE_WASM_INTERNAL_H

#include <jni.h>

void throwJavaException(JNIEnv *env, const char *exceptionClass, const char *fmt, ...);

jobject createJavaWrapper(JNIEnv* env, const char* className, jlong pointer);

jlong getPointerField(JNIEnv* env, jobject receiver);

jlong setPointerField(JNIEnv* env, jobject receiver, jlong newValue);

#endif //ZIPLINE_WASM_INTERNAL_H
