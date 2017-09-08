/*
 * Copyright (C) 2017 Square, Inc.
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
#ifndef DUKTAPE_ANDROID_LOCALFRAME_H
#define DUKTAPE_ANDROID_LOCALFRAME_H

#include <jni.h>

/**
 * RAII wrapper that allocates a new local reference frame for the JVM and releases it when leaving
 * scope.
 */
struct LocalFrame {
  LocalFrame(JNIEnv* env, std::size_t capacity)
      : m_env(*env) {
    if (m_env.PushLocalFrame(capacity)) {
      // Out of memory.
      throw std::bad_alloc();
    }
  }

  ~LocalFrame() {
    m_env.PopLocalFrame(nullptr);
  }

  // No copying allowed.
  LocalFrame(const LocalFrame&) = delete;
  LocalFrame& operator=(const LocalFrame&) = delete;

private:
  JNIEnv& m_env;
};

#endif //DUKTAPE_ANDROID_LOCALFRAME_H
