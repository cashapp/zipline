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
#ifndef DUKTAPE_ANDROID_CONTEXT_H
#define DUKTAPE_ANDROID_CONTEXT_H

#include <jni.h>
#include <vector>
#include "quickjs/quickjs.h"

class JSRuntime;
class JSContext;
class JsObjectProxy;

class Context {
public:
  Context(JNIEnv *env);
  ~Context();

  JsObjectProxy* getObjectProxy(JNIEnv*, jstring name, jobjectArray methods);
  void setObjectProxy(JNIEnv*, jstring name, jobject object, jobjectArray methods);
  jobject eval(JNIEnv*, jstring source, jstring file) const;
  typedef std::function<JSValueConst(const Context*, JNIEnv*, jvalue)> JavaToJavaScript;
  JavaToJavaScript getJavaToJsConverter(JNIEnv*, jclass type, bool boxed) const;
  typedef std::function<jvalue(const Context*, JNIEnv*, JSValueConst)> JavaScriptToJava;
  JavaScriptToJava getJsToJavaConverter(JNIEnv*, jclass type, bool boxed) const;

  jobject toJavaObject(JNIEnv*, const JSValue& value, bool throwOnUnsupportedType = true) const;
  void throwJsException(JNIEnv*, const JSValue& value) const;
  JSValue throwJavaExceptionFromJs(JNIEnv*) const;

  JNIEnv* getEnv() const;

  static JSValue jsCall(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv, int magic);

  JavaVM* javaVm;
  const jint jniVersion;
  JSRuntime *jsRuntime;
  JSContext *jsContext;
  jclass booleanClass;
  jclass integerClass;
  jclass doubleClass;
  jclass objectClass;
  jclass quickJsExceptionClass;
  jmethodID booleanValueOf;
  jmethodID booleanGetValue;
  jmethodID integerValueOf;
  jmethodID integerGetValue;
  jmethodID doubleValueOf;
  jmethodID doubleGetValue;
  jmethodID quickJsExceptionConstructor;
  std::vector<JsObjectProxy*> objectProxies;
};

std::string getName(JNIEnv* env, jobject javaClass);

#endif //DUKTAPE_ANDROID_CONTEXT_H
