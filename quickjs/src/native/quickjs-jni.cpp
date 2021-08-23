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
#include <jni.h>
#include <new>
#include "Context.h"
#include "JsObjectProxy.h"
#include "ExceptionThrowers.h"

extern "C" JNIEXPORT jlong JNICALL
Java_app_cash_quickjs_QuickJs_createContext(JNIEnv* env, jclass type) {
  Context* c = new(std::nothrow) Context(env);
  if (!c || !c->jsContext || !c->jsRuntime) {
    delete c;
    c = nullptr;
  }
  return reinterpret_cast<jlong>(c);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_quickjs_QuickJs_destroyContext(JNIEnv* env, jobject type, jlong context) {
  delete reinterpret_cast<Context*>(context);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_quickjs_QuickJs_evaluate__JLjava_lang_String_2Ljava_lang_String_2(JNIEnv* env,
                                                                                    jobject type,
                                                                                    jlong context_,
                                                                                    jstring sourceCode,
                                                                                    jstring fileName) {
  Context* context = reinterpret_cast<Context*>(context_);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return nullptr;
  }
  return context->eval(env, sourceCode, fileName);
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_cash_quickjs_QuickJs_get(JNIEnv* env, jobject thiz, jlong _context, jstring name,
                                      jobjectArray methods) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return 0L;
  }

  return reinterpret_cast<jlong>(context->getObjectProxy(env, name, methods));
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_quickjs_QuickJs_set(JNIEnv* env, jobject thiz, jlong _context, jstring name,
                                      jobject object, jobjectArray methods) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return;
  }
  context->setObjectProxy(env, name, object, methods);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_quickjs_QuickJs_call(JNIEnv* env, jobject thiz, jlong _context, jlong instance,
                                       jobject method, jobjectArray args) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return nullptr;
  }

  const JsObjectProxy* jsObjectProxy = reinterpret_cast<const JsObjectProxy*>(instance);
  if (!jsObjectProxy) {
    throwJavaException(env, "java/lang/NullPointerException", "Invalid JavaScript object");
    return nullptr;
  }

  return jsObjectProxy->call(context, env, method, args);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_quickjs_QuickJs_execute(JNIEnv* env, jobject thiz, jlong _context, jbyteArray bytecode) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException", "Null QuickJs context - did you close your QuickJs?");
    return nullptr;
  }
  return context->execute(env, bytecode);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_cash_quickjs_QuickJs_compile(JNIEnv* env, jobject thiz, jlong _context, jstring sourceCode, jstring fileName) {
  Context* context = reinterpret_cast<Context*>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException", "Null QuickJs context - did you close your QuickJs?");
    return nullptr;
  }
  return context->compile(env, sourceCode, fileName);
}
