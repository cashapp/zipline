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
#ifndef QUICKJS_ANDROID_EXCEPTIONTHROWERS_H
#define QUICKJS_ANDROID_EXCEPTIONTHROWERS_H

#include <jni.h>
#include "quickjs/quickjs.h"

class Context;

void throwJavaException(JNIEnv *env, const char *exceptionClass, const char *fmt, ...);
void throwJsExceptionFmt(JNIEnv *env, const Context *context, const char *fmt, ...);

#endif //QUICKJS_ANDROID_EXCEPTIONTHROWERS_H
