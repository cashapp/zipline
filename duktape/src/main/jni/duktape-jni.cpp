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
#include <mutex>
#include <jni.h>
#include "DuktapeContext.h"
#include "java/GlobalRef.h"
#include "java/JavaExceptions.h"

namespace {

  std::unique_ptr<GlobalRef> duktapeClass;
static jmethodID getLocalTimeZoneOffset = nullptr;

void initialize(JNIEnv* env, jclass type) {
  duktapeClass.reset(new GlobalRef(env, type));
  getLocalTimeZoneOffset = env->GetStaticMethodID(static_cast<jclass>(duktapeClass->get()),
                                                  "getLocalTimeZoneOffset",
                                                  "(D)I");
}

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
  static std::once_flag initDuktapeClass;
  std::call_once(initDuktapeClass, initialize, std::ref(env), type);

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
  delete reinterpret_cast<DuktapeContext*>(context);
}

JNIEXPORT jstring JNICALL
Java_com_squareup_duktape_Duktape_evaluate__JLjava_lang_String_2Ljava_lang_String_2(
    JNIEnv* env, jclass type, jlong context, jstring code, jstring fname) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return nullptr;
  }
  try {
    return duktape->evaluate(env, code, fname);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }
  return nullptr;
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_Duktape_set(JNIEnv *env, jclass type, jlong context, jstring name,
                                       jobject object, jobjectArray methods) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return;
  }
  try {
    duktape->set(env, name, object, methods);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }
}

JNIEXPORT jlong JNICALL
Java_com_squareup_duktape_Duktape_get(JNIEnv *env, jclass type, jlong context, jstring name,
                                        jobjectArray methods) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return 0L;
  }

  try {
    return reinterpret_cast<jlong>(duktape->get(env, name, methods));
  } catch (const std::invalid_argument& e) {
    queueIllegalArgumentException(env, e.what());
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }
  return 0L;
}

JNIEXPORT jobject JNICALL
Java_com_squareup_duktape_Duktape_call(JNIEnv *env, jclass type, jlong context, jlong instance,
                                       jobject method, jobjectArray args) {
  // Validate our DuktapeContext first - if the context is null, we can't use the proxy.
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return nullptr;
  }

  const JavaScriptObject* object = reinterpret_cast<const JavaScriptObject*>(instance);
  if (object == nullptr) {
    queueNullPointerException(env, "Invalid JavaScript object");
    return nullptr;
  }

  try {
    return object->call(env, method, args);
  } catch (const std::invalid_argument& e) {
    queueIllegalArgumentException(env, e.what());
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }
  return nullptr;
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_DuktapeScript_loadScript(JNIEnv *env, jclass type,
                                                                  jlong context, jstring script) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return;
  }

  try {
    return duktape->loadScript(env, script);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_DuktapeScript_closeScriptContext(JNIEnv *env, jclass type, jlong context) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return;
  }

  try {
    return duktape->closeScriptContext();
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_DuktapeScript_putDouble(JNIEnv *env, jclass type,
                                            jlong context, jstring key, jdouble value) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return;
  }

  try {
    return duktape->putDouble(env, key, value);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }
}

JNIEXPORT jdouble JNICALL
Java_com_squareup_duktape_DuktapeScript_getDouble(JNIEnv *env, jclass type,
                                                                  jlong context, jstring key) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return 0L;
  }

  try {
    return duktape->getDouble(env, key);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }

  return 0L;
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_DuktapeScript_putString(JNIEnv *env, jclass type,
                                            jlong context, jstring key, jstring value) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return;
  }

  try {
    return duktape->putString(env, key, value);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }
}

JNIEXPORT jstring JNICALL
Java_com_squareup_duktape_DuktapeScript_getString(JNIEnv *env, jclass type,
                                                                  jlong context, jstring key) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return nullptr;
  }

  try {
    return duktape->getString(env, key);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }

  return nullptr;
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_DuktapeScript_putLong(JNIEnv *env, jclass type, jlong context,
                                                jstring key, jlong value) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return;
  }

  try {
    duktape->putLong(env, key, value);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }
}

JNIEXPORT jlong JNICALL
Java_com_squareup_duktape_DuktapeScript_getLong(JNIEnv *env, jclass type,
                                                jlong context, jstring key) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return 0L;
  }

  try {
    return duktape->getLong(env, key);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }

  return 0L;
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_DuktapeScript_putBoolean(JNIEnv *env, jclass type, jlong context,
                                                   jstring key, jboolean value) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return;
  }

  try {
    duktape->putBoolean(env, key, value);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }
}

JNIEXPORT jboolean JNICALL
Java_com_squareup_duktape_DuktapeScript_getBoolean(JNIEnv *env, jclass type,
                                                   jlong context, jstring key) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return JNI_FALSE;
  }

  try {
    return duktape->getBoolean(env, key);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }

  return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_squareup_duktape_DuktapeScript_isNull__JLjava_lang_String_2(JNIEnv *env, jclass type,
                                                                     jlong context, jstring key) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return JNI_FALSE;
  }

  try {
    return duktape->isNull(env, key);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }

  return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_squareup_duktape_DuktapeScript_callFunction(JNIEnv *env, jclass type,
                                               jlong context, jstring key, jobjectArray args) {
  DuktapeContext* duktape = reinterpret_cast<DuktapeContext*>(context);
  if (duktape == nullptr) {
    queueNullPointerException(env, "Null Duktape context - did you close your Duktape?");
    return nullptr;
  }

  try {
    return duktape->callFunction(env, key, args);
  } catch (const std::runtime_error& e) {
    queueDuktapeException(env, e.what());
  }

  return nullptr;
}

} // extern "C"
