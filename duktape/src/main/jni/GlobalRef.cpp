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
#include "GlobalRef.h"

GlobalRef::GlobalRef(JNIEnv* env, jobject object)
    : m_object(env->NewGlobalRef(object)) {
  env->GetJavaVM(&m_javaVM);
}

GlobalRef::GlobalRef(const GlobalRef& other)
    : m_javaVM(other.m_javaVM)
    , m_object(getEnvFromJavaVM(m_javaVM)->NewGlobalRef(other.m_object)) {
}

GlobalRef& GlobalRef::operator=(const GlobalRef& other) {
  if (&other != this) {
    // Increment the new reference before decrementing the old.
    auto oldJVM = m_javaVM;
    auto oldObject = m_object;

    m_javaVM = other.m_javaVM;
    m_object = getEnvFromJavaVM(m_javaVM)->NewGlobalRef(other.m_object);

    getEnvFromJavaVM(oldJVM)->DeleteGlobalRef(oldObject);
  }
  return *this;
}

GlobalRef::~GlobalRef() {
  getEnvFromJavaVM(m_javaVM)->DeleteGlobalRef(m_object);
}

JNIEnv* GlobalRef::getJniEnv() const {
  return getEnvFromJavaVM(m_javaVM);
}

JNIEnv* getEnvFromJavaVM(JavaVM* javaVM) {
  if (javaVM == nullptr) {
    return nullptr;
  }

  JNIEnv* env;
  javaVM->AttachCurrentThread(
#ifdef __ANDROID__
      &env,
#else
      reinterpret_cast<void**>(&env),
#endif
      nullptr);
  return env;
}