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
#include <memory>
#include <jni.h>
#include "DuktapeContext.h"
#include "GlobalRef.h"

namespace {

std::unique_ptr<GlobalRef> duktapeClass;
static jmethodID getLocalTimeZoneOffset = nullptr;

} // anonymous namespace

extern "C" {

duk_int_t android__get_local_tzoffset(duk_double_t time) {
  if (!duktapeClass) {
    return 0;
  }
  JNIEnv* env = duktapeClass->getJniEnv();
  return env->CallStaticIntMethod(static_cast<jclass>(duktapeClass->get()),
                                  getLocalTimeZoneOffset,
                                  time);
}

JNIEXPORT jlong JNICALL
Java_com_squareup_duktape_Duktape_createContext(JNIEnv* env, jclass type) {
  if (!duktapeClass) {
    duktapeClass.reset(new GlobalRef(env, type));
    getLocalTimeZoneOffset = env->GetStaticMethodID(static_cast<jclass>(duktapeClass->get()),
                                                    "getLocalTimeZoneOffset",
                                                    "(D)I");
  }
  JavaVM* javaVM;
  env->GetJavaVM(&javaVM);
  try {
    return reinterpret_cast<jlong>(new DuktapeContext(javaVM));
  } catch (std::bad_alloc&) {
    return 0L;
  }
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_Duktape_destroyContext(JNIEnv *env, jclass type, jlong context) {
  delete reinterpret_cast<DuktapeContext *>(context);
}

JNIEXPORT jstring JNICALL
Java_com_squareup_duktape_Duktape_evaluate__JLjava_lang_String_2Ljava_lang_String_2(
    JNIEnv* env, jclass type, jlong context, jstring code, jstring fname) {
  DuktapeContext * duktape = reinterpret_cast<DuktapeContext *>(context);
  return duktape->evaluate(env, code, fname);
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_Duktape_bind__JLjava_lang_String_2Ljava_lang_Object_2_3Ljava_lang_Object_2(
    JNIEnv *env, jclass type, jlong context, jstring name, jobject object, jobjectArray methods) {
  DuktapeContext * duktape = reinterpret_cast<DuktapeContext *>(context);
  duktape->bindInstance(env, name, object, methods);
}

} // extern "C"
