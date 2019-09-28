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
#include "ExceptionThrowers.h"

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
  if (!typeName.empty() && typeName[0] == '[') {
    // type is an array.
    const jmethodID method = context->env->GetMethodID(context->env->GetObjectClass(type),
                                                       "getComponentType",
                                                       "()Ljava/lang/Class;");
    auto elementType = static_cast<jclass>(context->env->CallObjectMethod(type, method));
    const auto elementTypeName = getName(context->env, elementType);
    if (elementTypeName == "double") {
      return [](const Context* c, jvalue v) {
        if (!v.l) return JS_NULL;
        JSValue result = JS_NewArray(c->jsContext);
        auto elements = c->env->GetDoubleArrayElements(static_cast<jdoubleArray>(v.l), nullptr);
        for (jsize i = 0, e = c->env->GetArrayLength(static_cast<jarray>(v.l)); i < e; i++) {
          JS_SetPropertyUint32(c->jsContext, result, i, JS_NewFloat64(c->jsContext, elements[i]));
        }
        c->env->ReleaseDoubleArrayElements(static_cast<jdoubleArray>(v.l), elements, JNI_ABORT);
        return result;
      };
    } else if (elementTypeName == "int") {
      return [](const Context* c, jvalue v) {
        if (!v.l) return JS_NULL;
        JSValue result = JS_NewArray(c->jsContext);
        auto elements = c->env->GetIntArrayElements(static_cast<jintArray >(v.l), nullptr);
        for (jsize i = 0, e = c->env->GetArrayLength(static_cast<jarray>(v.l)); i < e; i++) {
          JS_SetPropertyUint32(c->jsContext, result, i, JS_NewInt32(c->jsContext, elements[i]));
        }
        c->env->ReleaseIntArrayElements(static_cast<jintArray>(v.l), elements, JNI_ABORT);
        return result;
      };
    } else if (elementTypeName == "boolean") {
      return [](const Context* c, jvalue v) {
        if (!v.l) return JS_NULL;
        JSValue result = JS_NewArray(c->jsContext);
        auto elements = c->env->GetBooleanArrayElements(static_cast<jbooleanArray>(v.l), nullptr);
        for (jsize i = 0, e = c->env->GetArrayLength(static_cast<jarray>(v.l)); i < e; i++) {
          JS_SetPropertyUint32(c->jsContext, result, i, JS_NewBool(c->jsContext, elements[i]));
        }
        c->env->ReleaseBooleanArrayElements(static_cast<jbooleanArray>(v.l), elements, JNI_ABORT);
        return result;
      };
    } else {
      auto converter = getJavaToJsConverter(context, elementType, boxed);
      return [converter](const Context* c, jvalue v) {
        if (!v.l) return JS_NULL;
        JSValue result = JS_NewArray(c->jsContext);
        jvalue element;
        for (jsize i = 0, e = c->env->GetArrayLength(static_cast<jarray>(v.l)); i < e; i++) {
          element.l = c->env->GetObjectArrayElement(static_cast<jobjectArray >(v.l), i);
          JS_SetPropertyUint32(c->jsContext, result, i, converter(c, element));
          c->env->DeleteLocalRef(element.l);
        }
        return result;
      };
    }
  }

  if (typeName == "java.lang.String") {
    return [](const Context* c, jvalue v) {
      if (!v.l) return JS_NULL;
      const auto s = c->env->GetStringUTFChars(static_cast<jstring>(v.l), nullptr);
      auto jsString = JS_NewString(c->jsContext, s);
      c->env->ReleaseStringUTFChars(static_cast<jstring>(v.l), s);
      return jsString;
    };
  } else if (typeName == "java.lang.Double" || (typeName == "double" && boxed)) {
    return [](const Context* c, jvalue v) {
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
  } else if (typeName == "java.lang.Object") {
    return [](const Context* c, jvalue v) {
      if (!v.l) return JS_NULL;
      return getJavaToJsConverter(c, c->env->GetObjectClass(v.l), true)(c, v);
    };
  } else {
    // Throw an exception for unsupported argument type.
    throwJavaException(context->env, "java/lang/IllegalArgumentException",
        "Unsupported Java type %s", typeName.c_str());
    return [](const Context*, jvalue) { return JS_NULL; };
  }
}

}

JsMethodProxy::JsMethodProxy(const Context* context, const char* name, jobject method)
    : name(name), methodId(context->env->FromReflectedMethod(method)) {
  JNIEnv* env = context->env;
  const jclass methodClass = env->GetObjectClass(method);

  const jmethodID isVarArgsMethod = env->GetMethodID(methodClass, "isVarArgs", "()Z");
  isVarArgs = env->CallBooleanMethod(method, isVarArgsMethod);

  const jmethodID getParameterTypes =
      env->GetMethodID(methodClass, "getParameterTypes", "()[Ljava/lang/Class;");
  jobjectArray parameterTypes =
      static_cast<jobjectArray>(env->CallObjectMethod(method, getParameterTypes));
  const jsize numArgs = env->GetArrayLength(parameterTypes);
  for (jsize i = 0; i < numArgs && !env->ExceptionCheck(); ++i) {
    auto parameterType = env->GetObjectArrayElement(parameterTypes, i);
    argumentLoaders.push_back(
        getJavaToJsConverter(context, static_cast<jclass>(parameterType), true));
    env->DeleteLocalRef(parameterType);
  }
  env->DeleteLocalRef(parameterTypes);
  env->DeleteLocalRef(methodClass);
}

jobject JsMethodProxy::call(Context* context, JSValue thisPointer, jobjectArray args) const {
  const auto totalArgs = std::min<int>(argumentLoaders.size(), context->env->GetArrayLength(args));
  std::vector<JSValue> arguments;
  int numArgs;
  jvalue arg;
  for (numArgs = 0; numArgs < totalArgs && !context->env->ExceptionCheck(); numArgs++) {
    arg.l = context->env->GetObjectArrayElement(args, numArgs);
    if (!isVarArgs || numArgs < totalArgs - 1) {
      arguments.push_back(argumentLoaders[numArgs](context, arg));
    } else {
      auto varArgs = argumentLoaders[numArgs](context, arg);
      if (JS_IsArray(context->jsContext, varArgs)) {
        auto len = JS_GetPropertyStr(context->jsContext, varArgs, "length");
        for (int i = 0, e = JS_VALUE_GET_INT(len); i < e; i++) {
          arguments.push_back(JS_GetPropertyUint32(context->jsContext, varArgs, i));
        }
        JS_FreeValue(context->jsContext, len);
        JS_FreeValue(context->jsContext, varArgs);
      } else {
        arguments.push_back(varArgs);
      }
    }
    context->env->DeleteLocalRef(arg.l);
  }

  jobject result;
  if (!context->env->ExceptionCheck()) {
    auto property = JS_NewAtom(context->jsContext, name.c_str());
    JSValue callResult = JS_Invoke(context->jsContext, thisPointer, property, arguments.size(),
                                   arguments.data());
    JS_FreeAtom(context->jsContext, property);
    result = context->toJavaObject(callResult);
    JS_FreeValue(context->jsContext, callResult);
  } else {
    result = nullptr;
  }
  for (JSValue argument : arguments) {
    JS_FreeValue(context->jsContext, argument);
  }

  return result;
}
