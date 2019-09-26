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

#include "JsMethodProxy.h"
#include <algorithm>
#include "Context.h"

namespace {

std::string getName(JNIEnv *env, jclass javaClass) {
  auto classType = env->GetObjectClass(javaClass);
  const jmethodID method = env->GetMethodID(classType, "getName", "()Ljava/lang/String;");
  auto javaString = static_cast<jstring>(env->CallObjectMethod(javaClass, method));
  const auto s = env->GetStringUTFChars(javaString, nullptr);

  std::string str(s);
  env->ReleaseStringUTFChars(javaString, s);
  env->DeleteLocalRef(javaString);
  env->DeleteLocalRef(classType);
  return str;
}

JsMethodProxy::ArgumentLoader
getJavaToJsConverter(const Context* context, jclass type, bool boxed) {
  const auto typeName = getName(context->env, type);
  if (typeName == "java.lang.String") {
    return [](const Context* c, jvalue v) {
      if (!v.l) return JS_NULL;
      const auto s = c->env->GetStringUTFChars(static_cast<jstring>(v.l), nullptr);
      auto jsString = JS_NewString(c->jsContext, s);
      c->env->ReleaseStringUTFChars(static_cast<jstring>(v.l), s);
      return jsString;
    };
  } else if (typeName == "java.lang.Double" || (typeName == "double" && boxed)) {
    return [](const Context *c, jvalue v) {
      return v.l != nullptr
             ? JS_NewFloat64(c->jsContext, c->env->CallDoubleMethod(v.l, c->doubleGetValue))
             : JS_NULL;
    };
  } else if (typeName == "java.lang.Integer" || (typeName == "int" && boxed)) {
    return [](const Context* c, jvalue v) {
      return v.l != nullptr
             ? JS_NewInt32(c->jsContext, c->env->CallIntMethod(v.l, c->integerGetValue))
             : JS_NULL;
    };
  } else if (typeName == "java.lang.Boolean" || (typeName == "boolean" && boxed)) {
    return [](const Context* c, jvalue v) {
      return v.l != nullptr
             ? JS_NewBool(c->jsContext, c->env->CallBooleanMethod(v.l, c->booleanGetValue))
             : JS_NULL;
    };
  } else if (typeName == "double") {
    return [](const Context* c, jvalue v) {
      return JS_NewFloat64(c->jsContext, v.d);
    };
  } else if (typeName == "int") {
    return [](const Context* c, jvalue v) {
      return JS_NewInt32(c->jsContext, v.i);
    };
  } else if (typeName == "boolean") {
    return [](const Context* c, jvalue v) {
      return JS_NewBool(c->jsContext, v.z);
    };
  } else {
    // TODO: throw an exception for unsupported argument type.
    return [](const Context*, jvalue) { return JS_NULL; };
  }
}

}

JsMethodProxy::JsMethodProxy(const Context* context, const char *name, jobject method)
    : name(name), methodId(context->env->FromReflectedMethod(method)) {
  JNIEnv* env = context->env;
  const jclass methodClass = env->GetObjectClass(method);

  const jmethodID getReturnType =
      env->GetMethodID(methodClass, "getReturnType", "()Ljava/lang/Class;");
  const auto returnedClass = static_cast<jclass>(env->CallObjectMethod(method, getReturnType));

  const jmethodID isVarArgsMethod = env->GetMethodID(methodClass, "isVarArgs", "()Z");
  isVarArgs = false; // TODO: env->CallBooleanMethod(method, isVarArgsMethod);

  const jmethodID getParameterTypes =
      env->GetMethodID(methodClass, "getParameterTypes", "()[Ljava/lang/Class;");
  jobjectArray parameterTypes =
      static_cast<jobjectArray>(env->CallObjectMethod(method, getParameterTypes));
  const jsize numArgs = env->GetArrayLength(parameterTypes);
  argumentLoaders.resize(numArgs);
  for (jsize i = 0; i < numArgs; ++i) {
    auto parameterType = env->GetObjectArrayElement(parameterTypes, i);
    if (isVarArgs && i == numArgs - 1) {
      const auto parameterClass = env->GetObjectClass(parameterType);
      const jmethodID getComponentType =
          env->GetMethodID(parameterClass, "getComponentType", "()Ljava/lang/Class;");
      auto parameterComponentType = env->CallObjectMethod(parameterType, getComponentType);
      argumentLoaders[i] =
          getJavaToJsConverter(context, static_cast<jclass>(parameterComponentType), false);
      env->DeleteLocalRef(parameterComponentType);
      env->DeleteLocalRef(parameterClass);
      break;
    } else {
      argumentLoaders[i] = getJavaToJsConverter(context, static_cast<jclass>(parameterType), true);
    }
    env->DeleteLocalRef(parameterType);
  }
  env->DeleteLocalRef(parameterTypes);
  env->DeleteLocalRef(methodClass);
}

jobject JsMethodProxy::call(Context *context, JSValue thisPointer, jobjectArray args) const {
  const auto totalArgs = std::min<int>(argumentLoaders.size(), context->env->GetArrayLength(args));
  JSValueConst arguments[totalArgs];
  int numArgs;
  jvalue arg;
  for (numArgs = 0; numArgs < totalArgs && !context->env->ExceptionCheck(); numArgs++) {
    arg.l = context->env->GetObjectArrayElement(args, numArgs);
    arguments[numArgs] = argumentLoaders[numArgs](context, arg);
    context->env->DeleteLocalRef(arg.l);
  }

  jobject result;
  if (!context->env->ExceptionCheck()) {
    auto property = JS_NewAtom(context->jsContext, name.c_str());
    JSValue callResult = JS_Invoke(context->jsContext, thisPointer, property, numArgs, arguments);
    JS_FreeAtom(context->jsContext, property);
    result = context->toJavaObject(callResult);
    JS_FreeValue(context->jsContext, callResult);
  } else {
    result = nullptr;
  }
  for (int i = 0; i < numArgs; i++) {
    JS_FreeValue(context->jsContext, arguments[i]);
  }

  return result;
}
