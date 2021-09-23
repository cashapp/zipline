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
#ifndef QUICKJS_ANDROID_CONTEXT_H
#define QUICKJS_ANDROID_CONTEXT_H

#include <jni.h>
#include <string>
#include <vector>
#include <unordered_map>
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
  jobject eval(JNIEnv*, jstring source, jstring file);
  jobject execute(JNIEnv*, jbyteArray byteCode);
  jbyteArray compile(JNIEnv*, jstring source, jstring file);
  void setInterruptHandler(JNIEnv* env, jobject interruptHandler);
  jobject memoryUsage(JNIEnv*);
  void setMemoryLimit(JNIEnv* env, jlong limit);
  void setGcThreshold(JNIEnv* env, jlong gcThreshold);
  void setMaxStackSize(JNIEnv* env, jlong stackSize);
  typedef std::function<JSValueConst(Context*, JNIEnv*, jvalue)> JavaToJavaScript;
  JavaToJavaScript getJavaToJsConverter(JNIEnv*, jclass type, bool boxed);
  typedef std::function<jvalue(Context*, JNIEnv*, JSValueConst)> JavaScriptToJava;
  JavaScriptToJava getJsToJavaConverter(JNIEnv*, jclass type, bool boxed);

  jobject toJavaObject(JNIEnv*, const JSValue& value, bool throwOnUnsupportedType = true);
  void throwJsException(JNIEnv*, const JSValue& value) const;
  JSValue throwJavaExceptionFromJs(JNIEnv*) const;

  JNIEnv* getEnv() const;

  static JSValue
  jsCall(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv, int magic);

  jclass getGlobalRef(JNIEnv* env, jclass clazz);
  std::string toCppString(JNIEnv* env, jstring string) const;
  jstring toJavaString(JNIEnv* env, const JSValueConst& value) const;
  jobject toJavaByteArray(JNIEnv* env, const JSValueConst& value) const;
  JSValue toJsByteArray(JNIEnv* env, jbyteArray value) const;

  JavaVM* javaVm;
  const jint jniVersion;
  JSRuntime *jsRuntime;
  JSContext *jsContext;
  JSClassID jsClassId;
  jclass booleanClass;
  jclass integerClass;
  jclass doubleClass;
  jclass objectClass;
  jclass stringClass;
  jclass memoryUsageClass;
  jstring stringUtf8;
  jclass quickJsExceptionClass;
  jmethodID booleanValueOf;
  jmethodID booleanGetValue;
  jmethodID integerValueOf;
  jmethodID integerGetValue;
  jmethodID doubleValueOf;
  jmethodID doubleGetValue;
  jmethodID stringGetBytes;
  jmethodID stringConstructor;
  jmethodID memoryUsageConstructor;
  jmethodID quickJsExceptionConstructor;
  jclass interruptHandlerClass;
  jmethodID interruptHandlerPoll;
  jobject interruptHandler;
  std::vector<JsObjectProxy*> objectProxies;
  std::unordered_map<std::string, jclass> globalReferences;
};

std::string getName(JNIEnv* env, jobject javaClass);

#endif //QUICKJS_ANDROID_CONTEXT_H
