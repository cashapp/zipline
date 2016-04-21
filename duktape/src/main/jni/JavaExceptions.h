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
#ifndef DUKTAPE_ANDROID_JAVAEXCEPTIONS_H
#define DUKTAPE_ANDROID_JAVAEXCEPTIONS_H

#include <jni.h>
#include "duktape.h"
#include <string>

void queueIllegalArgumentException(JNIEnv* env, const std::string& message);

void queueDuktapeException(JNIEnv* env, const std::string& message);

void queueNullPointerException(JNIEnv* env, const std::string& message);

/**
 * Determines if an exception has been thrown in this JNI thread.  If so, creates a Duktape error
 * with the Java exception embedded in it, and throws it.
 */
void checkRethrowDuktapeError(JNIEnv *env, duk_context *ctx);

/**
 * Sets up a Java {@code DuktapeException} based on the Duktape JavaScript error at the top of the
 * Duktape stack. The exception will be thrown to the Java caller when the current JNI call returns.
 */
void queueJavaExceptionForDuktapeError(JNIEnv *env, duk_context *ctx);

#endif //DUKTAPE_ANDROID_JAVAEXCEPTIONS_H
