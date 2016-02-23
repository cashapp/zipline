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
#ifndef DUKTAPE_ANDROID_JSTRING_H
#define DUKTAPE_ANDROID_JSTRING_H

#include <string>
#include <jni.h>

class JString {
public:
  JString(JNIEnv* env, jstring s)
      : m_env(*env)
      , m_jstring(s)
      , m_str(m_env.GetStringUTFChars(m_jstring, 0)) {
  }

  ~JString() {
    m_env.ReleaseStringUTFChars(m_jstring, m_str);
  }

  operator const char* () const {
    return m_str;
  }

  std::string str() const {
    return m_str;
  }

private:
  JNIEnv& m_env;
  const jstring m_jstring;
  const char* m_str;
};

#endif //DUKTAPE_ANDROID_JSTRING_H
