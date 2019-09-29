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
#include "Context.h"
#include <cstring>
#include "JsObjectProxy.h"
#include "JsMethodProxy.h"
#include "ExceptionThrowers.h"

Context::Context(JNIEnv *env)
    : env(env), jsRuntime(JS_NewRuntime()), jsContext(JS_NewContext(jsRuntime)),
      booleanClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Boolean")))),
      integerClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Integer")))),
      doubleClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Double")))),
      objectClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Object")))),
      quickJsExceptionClass(static_cast<jclass>(env->NewGlobalRef(
          env->FindClass("com/squareup/quickjs/QuickJsException")))),
      booleanValueOf(env->GetStaticMethodID(booleanClass, "valueOf", "(Z)Ljava/lang/Boolean;")),
      booleanGetValue(env->GetMethodID(booleanClass, "booleanValue", "()Z")),
      integerValueOf(env->GetStaticMethodID(integerClass, "valueOf", "(I)Ljava/lang/Integer;")),
      integerGetValue(env->GetMethodID(integerClass, "intValue", "()I")),
      doubleValueOf(env->GetStaticMethodID(doubleClass, "valueOf", "(D)Ljava/lang/Double;")),
      doubleGetValue(env->GetMethodID(doubleClass, "doubleValue", "()D")),
      quickJsExceptionConstructor(env->GetMethodID(quickJsExceptionClass, "<init>",
                                                   "(Ljava/lang/String;Ljava/lang/String;)V")) {
}

Context::~Context() {
  for (auto proxy : objectProxies) {
    delete proxy;
  }
  env->DeleteGlobalRef(quickJsExceptionClass);
  env->DeleteGlobalRef(objectClass);
  env->DeleteGlobalRef(doubleClass);
  env->DeleteGlobalRef(integerClass);
  env->DeleteGlobalRef(booleanClass);
  JS_FreeContext(jsContext);
  JS_FreeRuntime(jsRuntime);
}

JsObjectProxy *Context::createObjectProxy(jstring name, jobjectArray methods) {
  JSValue global = JS_GetGlobalObject(jsContext);

  const char *nameStr = env->GetStringUTFChars(name, 0);

  JSValue obj = JS_GetPropertyStr(jsContext, global, nameStr);

  JsObjectProxy *jsObjectProxy = nullptr;
  if (JS_IsObject(obj)) {
    jsObjectProxy = new JsObjectProxy(nameStr);
    jsize numMethods = env->GetArrayLength(methods);
    jmethodID getName = nullptr;
    for (int i = 0; i < numMethods && !env->ExceptionCheck(); ++i) {
      jobject method = env->GetObjectArrayElement(methods, i);
      if (!getName) {
        jclass methodClass = env->GetObjectClass(method);
        getName = env->GetMethodID(methodClass, "getName", "()Ljava/lang/String;");
      }
      jstring methodName = static_cast<jstring>(env->CallObjectMethod(method, getName));
      const char *methodNameStr = env->GetStringUTFChars(methodName, 0);

      JSValue prop = JS_GetPropertyStr(jsContext, obj, methodNameStr);
      if (JS_IsFunction(jsContext, prop)) {
        jsObjectProxy->methods.push_back(new JsMethodProxy(this, methodNameStr, method));
      } else {
        const char *msg = JS_IsUndefined(prop)
                          ? "JavaScript global %s has no method called %s"
                          : "JavaScript property %s.%s not callable";
        throwJsExceptionFmt(env, this, msg, nameStr, methodNameStr);
      }
      JS_FreeValue(jsContext, prop);
      env->ReleaseStringUTFChars(methodName, methodNameStr);
    }
    if (!env->ExceptionCheck()) {
      objectProxies.push_back(jsObjectProxy);
    } else {
      delete jsObjectProxy;
      jsObjectProxy = nullptr;
    }
  } else if (JS_IsException(obj)) {
    throwJsException(env, this, obj);
  } else {
    const char *msg = JS_IsUndefined(obj)
                      ? "A global JavaScript object called %s was not found"
                      : "JavaScript global called %s is not an object";
    throwJavaException(env, "java/lang/IllegalArgumentException", msg, nameStr);
  }

  JS_FreeValue(jsContext, obj);

  env->ReleaseStringUTFChars(name, nameStr);
  JS_FreeValue(jsContext, global);

  return jsObjectProxy;
}

jobject Context::toJavaObject(JSValue value) const {
  jobject result;
  switch (JS_VALUE_GET_TAG(value)) {
    case JS_TAG_EXCEPTION: {
      throwJsException(env, this, value);
      result = nullptr;
      break;
    }

    case JS_TAG_STRING: {
      const char *string = JS_ToCString(jsContext, value);
      result = env->NewStringUTF(string);
      JS_FreeCString(jsContext, string);
      break;
    }

    case JS_TAG_BOOL: {
      jvalue v;
      v.z = static_cast<jboolean>(JS_VALUE_GET_BOOL(value));
      result = env->CallStaticObjectMethodA(booleanClass, booleanValueOf, &v);
      break;
    }

    case JS_TAG_INT: {
      jvalue v;
      v.j = static_cast<jint>(JS_VALUE_GET_INT(value));
      result = env->CallStaticObjectMethodA(integerClass, integerValueOf, &v);
      break;
    }

    case JS_TAG_FLOAT64: {
      jvalue v;
      v.d = static_cast<jdouble>(JS_VALUE_GET_FLOAT64(value));
      result = env->CallStaticObjectMethodA(doubleClass, doubleValueOf, &v);
      break;
    }

    case JS_TAG_OBJECT:
      if (JS_IsArray(jsContext, value)) {
        const auto arrayLength = JS_VALUE_GET_INT(JS_GetPropertyStr(jsContext, value, "length"));
        result = env->NewObjectArray(arrayLength, objectClass, nullptr);
        for (int i = 0; i < arrayLength && !env->ExceptionCheck(); i++) {
          auto element = JS_GetPropertyUint32(jsContext, value, i);
          env->SetObjectArrayElement(static_cast<jobjectArray>(result), i, toJavaObject(element));
          JS_FreeValue(jsContext, element);
        }
        break;
      }
      // Fall through.

    case JS_TAG_NULL:
    case JS_TAG_UNDEFINED:
    default:
      result = nullptr;
      break;
  }
  return result;
}

jobject Context::eval(jstring source, jstring file) const {
  const char *sourceCode = env->GetStringUTFChars(source, 0);
  const char *fileName = env->GetStringUTFChars(file, 0);

  JSValue evalValue = JS_Eval(jsContext, sourceCode, strlen(sourceCode), fileName, 0);

  env->ReleaseStringUTFChars(source, sourceCode);
  env->ReleaseStringUTFChars(file, fileName);

  jobject result = toJavaObject(evalValue);

  JS_FreeValue(jsContext, evalValue);

  return result;
}