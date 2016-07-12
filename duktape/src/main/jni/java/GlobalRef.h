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
#ifndef DUKTAPE_ANDROID_GLOBALREF_H
#define DUKTAPE_ANDROID_GLOBALREF_H

#include <jni.h>

/**
 * Manages a global reference to a {@code jobject}.  Copying a {@code GlobalRef} increments the
 * underlying global reference count on the {@code object} in the JVM.
 */
class GlobalRef {
public:
  GlobalRef(JNIEnv* env, jobject object);
  GlobalRef(const GlobalRef& other);
  GlobalRef& operator=(const GlobalRef& other);
  ~GlobalRef();

  jobject get() const {
    return m_object;
  }

  JNIEnv* getJniEnv() const;

private:
  JavaVM* m_javaVM;
  jobject m_object;
};

JNIEnv* getEnvFromJavaVM(JavaVM* javaVM);

#endif //DUKTAPE_ANDROID_GLOBALREF_H
