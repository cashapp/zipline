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
#include <jni.h>
#include "DuktapeContext.h"
#include "GlobalRef.h"

namespace {

static JavaVM *jvm = nullptr;
static jclass duktapeClass = nullptr;
static jmethodID getLocalTimeZoneOffset = nullptr;

} // anonymous namespace

extern "C" {

duk_int_t android__get_local_tzoffset(duk_double_t time) {
  JNIEnv* env = getEnvFromJavaVM(jvm);
  return env->CallStaticIntMethod(duktapeClass, getLocalTimeZoneOffset, time);
}

JNIEXPORT jlong JNICALL
Java_com_squareup_duktape_Duktape_createContext(JNIEnv* env, jclass type) {
  if (jvm == nullptr) {
    env->GetJavaVM(&jvm);
    duktapeClass = reinterpret_cast<jclass>(env->NewGlobalRef(type));
    getLocalTimeZoneOffset = env->GetStaticMethodID(duktapeClass, "getLocalTimeZoneOffset", "(D)I");
  }
  return reinterpret_cast<jlong>(new DuktapeContext(jvm));
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
