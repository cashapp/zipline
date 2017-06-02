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
#ifndef DUKTAPE_ANDROID_DUKTAPE_CONTEXT_H
#define DUKTAPE_ANDROID_DUKTAPE_CONTEXT_H

#include <jni.h>
#include <list>
#include "duktape/duktape.h"
#include "javascript/JavaScriptObject.h"
#include "java/JavaType.h"

class DuktapeContext {
public:
  explicit DuktapeContext(JavaVM* javaVM);
  ~DuktapeContext();
  DuktapeContext(const DuktapeContext &) = delete;
  DuktapeContext & operator=(const DuktapeContext &) = delete;

  jobject evaluate(JNIEnv* env, jstring sourceCode, jstring fileName) const;

  void set(JNIEnv *env, jstring name, jobject object, jobjectArray methods);

  const JavaScriptObject* get(JNIEnv *env, jstring name, jobjectArray methods);

  void loadScript(JNIEnv *env, jstring script);

  void closeScriptContext();

  void putDouble(JNIEnv *env, jstring key, jdouble value);

  jdouble getDouble(JNIEnv *env, jstring key);

  void putString(JNIEnv *env, jstring key, jstring value);

  jstring getString(JNIEnv *env, jstring key);

  void putLong(JNIEnv *env, jstring key, jlong value);

  jlong getLong(JNIEnv *env, jstring key);

  void putBoolean(JNIEnv *env, jstring key, jboolean value);

  jboolean getBoolean(JNIEnv *env, jstring key);

  jstring callFunction(JNIEnv *env, jstring key, jobjectArray args);

  jboolean isNull(JNIEnv *env, jstring key);

private:
  duk_context* m_context;
  std::list<JavaScriptObject> m_jsObjects;
  JavaTypeMap m_javaValues;
  const JavaType* m_objectType;
};

#endif // DUKTAPE_ANDROID_DUKTAPE_CONTEXT_H
