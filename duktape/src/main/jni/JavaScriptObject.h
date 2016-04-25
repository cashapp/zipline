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
#ifndef DUKTAPE_ANDROID_JAVASCRIPTOBJECT_H
#define DUKTAPE_ANDROID_JAVASCRIPTOBJECT_H

#include <string>
#include <functional>
#include <unordered_map>
#include <jni.h>
#include "duktape.h"

/** The class represents a global JavaScript object that can be called from Java. */
class JavaScriptObject {
public:
  JavaScriptObject(JNIEnv* env, duk_context* context, jstring name, jobjectArray methods);
  ~JavaScriptObject();
  JavaScriptObject(const JavaScriptObject&) = delete;
  JavaScriptObject& operator=(const JavaScriptObject&) = delete;

  jobject call(JNIEnv* env, jobject method, jobjectArray args) const;

  /**
   * Defines a functor to invoke a JavaScript method marshaling the given Java arguments and
   * return value.  If the JavaScript method throws an error, the functor will throw a
   * DuktapeException to the caller.
   */
  typedef std::function<jobject(JNIEnv*, duk_context*, void*, jobjectArray)> MethodBody;

private:
  static duk_ret_t finalizer(duk_context* ctx);

  const std::string m_name;
  duk_context* m_context;
  void* m_instance;
  std::unordered_map<jmethodID, MethodBody> m_methods;
  duk_c_function m_nextFinalizer;
};


#endif //DUKTAPE_ANDROID_JAVASCRIPTOBJECT_H
