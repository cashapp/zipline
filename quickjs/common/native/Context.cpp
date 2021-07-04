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
#include <memory>
#include <assert.h>
#include "JavaObjectProxy.h"
#include "JsObjectProxy.h"
#include "JsMethodProxy.h"
#include "ExceptionThrowers.h"
#include "quickjs/quickjs.h"

std::string getName(JNIEnv* env, jobject javaClass) {
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

namespace {

void jsFinalize(JSRuntime* jsRuntime, JSValue val) {
  auto context = reinterpret_cast<const Context*>(JS_GetRuntimeOpaque(jsRuntime));
  if (context) {
    delete reinterpret_cast<JavaObjectProxy*>(JS_GetOpaque(val, context->jsClassId));
  }
}

struct JniThreadDetacher {
  JavaVM& javaVm;

  JniThreadDetacher(JavaVM* javaVm) : javaVm(*javaVm) {
  }

  ~JniThreadDetacher() {
    javaVm.DetachCurrentThread();
  }
};

} // anonymous namespace

Context::Context(JNIEnv* env)
    : jniVersion(env->GetVersion()), jsRuntime(JS_NewRuntime()),
      jsContext(JS_NewContext(jsRuntime)), jsClassId(0),
      booleanClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Boolean")))),
      integerClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Integer")))),
      doubleClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Double")))),
      objectClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Object")))),
      quickJsExceptionClass(static_cast<jclass>(env->NewGlobalRef(
          env->FindClass("app/cash/quickjs/QuickJsException")))),
      booleanValueOf(env->GetStaticMethodID(booleanClass, "valueOf", "(Z)Ljava/lang/Boolean;")),
      booleanGetValue(env->GetMethodID(booleanClass, "booleanValue", "()Z")),
      integerValueOf(env->GetStaticMethodID(integerClass, "valueOf", "(I)Ljava/lang/Integer;")),
      integerGetValue(env->GetMethodID(integerClass, "intValue", "()I")),
      doubleValueOf(env->GetStaticMethodID(doubleClass, "valueOf", "(D)Ljava/lang/Double;")),
      doubleGetValue(env->GetMethodID(doubleClass, "doubleValue", "()D")),
      quickJsExceptionConstructor(env->GetMethodID(quickJsExceptionClass, "<init>",
                                                   "(Ljava/lang/String;Ljava/lang/String;)V")) {
  env->GetJavaVM(&javaVm);
  JS_SetRuntimeOpaque(jsRuntime, this);
}

Context::~Context() {
  for (auto proxy : objectProxies) {
    delete proxy;
  }
  auto env = getEnv();
  for (auto refs : globalReferences) {
    env->DeleteGlobalRef(refs.second);
  }
  env->DeleteGlobalRef(quickJsExceptionClass);
  env->DeleteGlobalRef(objectClass);
  env->DeleteGlobalRef(doubleClass);
  env->DeleteGlobalRef(integerClass);
  env->DeleteGlobalRef(booleanClass);
  JS_FreeContext(jsContext);
  JS_SetRuntimeOpaque(jsRuntime, nullptr);
  JS_FreeRuntime(jsRuntime);
}

JsObjectProxy* Context::getObjectProxy(JNIEnv* env, jstring name, jobjectArray methods) {
  JSValue global = JS_GetGlobalObject(jsContext);

  const char* nameStr = env->GetStringUTFChars(name, 0);

  JSValue obj = JS_GetPropertyStr(jsContext, global, nameStr);

  JsObjectProxy* jsObjectProxy = nullptr;
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
      const char* methodNameStr = env->GetStringUTFChars(methodName, 0);

      JSValue prop = JS_GetPropertyStr(jsContext, obj, methodNameStr);
      if (JS_IsFunction(jsContext, prop)) {
        jsObjectProxy->methods.push_back(new JsMethodProxy(this, env, methodNameStr, method));
      } else {
        const char* msg = JS_IsUndefined(prop)
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
    throwJsException(env, obj);
  } else {
    const char* msg = JS_IsUndefined(obj)
                      ? "A global JavaScript object called %s was not found"
                      : "JavaScript global called %s is not an object";
    throwJavaException(env, "java/lang/IllegalArgumentException", msg, nameStr);
  }

  JS_FreeValue(jsContext, obj);

  env->ReleaseStringUTFChars(name, nameStr);
  JS_FreeValue(jsContext, global);

  return jsObjectProxy;
}

void Context::setObjectProxy(JNIEnv* env, jstring name, jobject object, jobjectArray methods) {
  auto global = JS_GetGlobalObject(jsContext);

  const char* nameStr = env->GetStringUTFChars(name, 0);

  const auto objName = JS_NewAtom(jsContext, nameStr);
  if (!JS_HasProperty(jsContext, global, objName)) {
    if (jsClassId == 0) {
      JS_NewClassID(&jsClassId);
      JSClassDef classDef;
      memset(&classDef, 0, sizeof(JSClassDef));
      classDef.class_name = "QuickJsAndroidProxy";
      classDef.finalizer = jsFinalize;
      if (JS_NewClass(jsRuntime, jsClassId, &classDef)) {
        jsClassId = 0;
        throwJavaException(env, "java/lang/NullPointerException",
                           "Failed to allocate JavaScript proxy class");
      }
    }
    if (jsClassId != 0) {
      auto proxy = JS_NewObjectClass(jsContext, jsClassId);
      if (JS_IsException(proxy) || JS_SetProperty(jsContext, global, objName, proxy) <= 0) {
        throwJsException(env, proxy);
      } else {
        std::unique_ptr<JavaObjectProxy> javaObject(new JavaObjectProxy(this, env, nameStr, object, methods, proxy));
        if (!env->ExceptionCheck()) {
          JS_SetOpaque(proxy, javaObject.release());
        }
      }
    }
  } else {
    throwJavaException(env, "java/lang/IllegalArgumentException",
                       "A global object called %s already exists", nameStr);
  }
  JS_FreeAtom(jsContext, objName);
  env->ReleaseStringUTFChars(name, nameStr);
  JS_FreeValue(jsContext, global);
}

jobject
Context::toJavaObject(JNIEnv* env, const JSValueConst& value, bool throwOnUnsupportedType) {
  jobject result;
  switch (JS_VALUE_GET_NORM_TAG(value)) {
    case JS_TAG_EXCEPTION: {
      throwJsException(env, value);
      result = nullptr;
      break;
    }

    case JS_TAG_STRING: {
      const char* string = JS_ToCString(jsContext, value);
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

    case JS_TAG_NULL:
    case JS_TAG_UNDEFINED:
      result = nullptr;
      break;

    case JS_TAG_OBJECT:
      if (JS_IsArray(jsContext, value)) {
        const auto arrayLength = JS_VALUE_GET_INT(JS_GetPropertyStr(jsContext, value, "length"));
        result = env->NewObjectArray(arrayLength, objectClass, nullptr);
        for (int i = 0; i < arrayLength && !env->ExceptionCheck(); i++) {
          auto element = JS_GetPropertyUint32(jsContext, value, i);
          auto javaElement = toJavaObject(env, element);
          if (!env->ExceptionCheck()) {
            env->SetObjectArrayElement(static_cast<jobjectArray>(result), i, javaElement);
          }
          JS_FreeValue(jsContext, element);
        }
        break;
      }
      // Fall through.
    default:
      if (throwOnUnsupportedType) {
        auto str = JS_ToCString(jsContext, value);
        throwJsExceptionFmt(env, this, "Cannot marshal value %s to Java", str);
        JS_FreeCString(jsContext, str);
      }
      result = nullptr;
      break;
  }
  return result;
}

jobject Context::eval(JNIEnv* env, jstring source, jstring file) {
  const char* sourceCode = env->GetStringUTFChars(source, 0);
  const char* fileName = env->GetStringUTFChars(file, 0);

  JSValue evalValue = JS_Eval(jsContext, sourceCode, strlen(sourceCode), fileName, 0);

  env->ReleaseStringUTFChars(source, sourceCode);
  env->ReleaseStringUTFChars(file, fileName);

  jobject result = toJavaObject(env, evalValue, false);

  JS_FreeValue(jsContext, evalValue);

  return result;
}

Context::JavaToJavaScript
Context::getJavaToJsConverter(JNIEnv* env, jclass type, bool boxed) {
  const auto typeName = getName(env, type);
  if (!typeName.empty() && typeName[0] == '[') {
    // type is an array.
    const jmethodID method = env->GetMethodID(env->GetObjectClass(type),
                                              "getComponentType",
                                              "()Ljava/lang/Class;");
    auto elementType = static_cast<jclass>(env->CallObjectMethod(type, method));
    const auto elementTypeName = getName(env, elementType);
    if (elementTypeName == "double") {
      return [](Context* c, JNIEnv* env, jvalue v) {
        if (!v.l) return JS_NULL;
        JSValue result = JS_NewArray(c->jsContext);
        auto elements = env->GetDoubleArrayElements(static_cast<jdoubleArray>(v.l), nullptr);
        const auto length = env->GetArrayLength(static_cast<jarray>(v.l));
        for (jsize i = 0; i < length && !env->ExceptionCheck(); i++) {
          JS_SetPropertyUint32(c->jsContext, result, i, JS_NewFloat64(c->jsContext, elements[i]));
        }
        env->ReleaseDoubleArrayElements(static_cast<jdoubleArray>(v.l), elements, JNI_ABORT);
        if (env->ExceptionCheck()) {
          c->throwJavaExceptionFromJs(env);
        }
        return result;
      };
    } else if (elementTypeName == "int") {
      return [](Context* c, JNIEnv* env, jvalue v) {
        if (!v.l) return JS_NULL;
        JSValue result = JS_NewArray(c->jsContext);
        auto elements = env->GetIntArrayElements(static_cast<jintArray >(v.l), nullptr);
        const auto length = env->GetArrayLength(static_cast<jarray>(v.l));
        for (jsize i = 0; i < length && !env->ExceptionCheck(); i++) {
          JS_SetPropertyUint32(c->jsContext, result, i, JS_NewInt32(c->jsContext, elements[i]));
        }
        env->ReleaseIntArrayElements(static_cast<jintArray>(v.l), elements, JNI_ABORT);
        if (env->ExceptionCheck()) {
          c->throwJavaExceptionFromJs(env);
        }
        return result;
      };
    } else if (elementTypeName == "boolean") {
      return [](Context* c, JNIEnv* env, jvalue v) {
        if (!v.l) return JS_NULL;
        JSValue result = JS_NewArray(c->jsContext);
        auto elements = env->GetBooleanArrayElements(static_cast<jbooleanArray>(v.l), nullptr);
        const auto length = env->GetArrayLength(static_cast<jarray>(v.l));
        for (jsize i = 0; i < length && !env->ExceptionCheck(); i++) {
          JS_SetPropertyUint32(c->jsContext, result, i, JS_NewBool(c->jsContext, elements[i]));
        }
        env->ReleaseBooleanArrayElements(static_cast<jbooleanArray>(v.l), elements, JNI_ABORT);
        if (env->ExceptionCheck()) {
          c->throwJavaExceptionFromJs(env);
        }
        return result;
      };
    } else {
      auto converter = getJavaToJsConverter(env, elementType, true);
      return [converter](Context* c, JNIEnv* env, jvalue v) {
        if (!v.l) return JS_NULL;
        JSValue result = JS_NewArray(c->jsContext);
        jvalue element;
        const auto length = env->GetArrayLength(static_cast<jarray>(v.l));
        for (jsize i = 0; i < length && !env->ExceptionCheck(); i++) {
          element.l = env->GetObjectArrayElement(static_cast<jobjectArray >(v.l), i);
          if (!env->ExceptionCheck()) {
            JS_SetPropertyUint32(c->jsContext, result, i, converter(c, env, element));
          }
          env->DeleteLocalRef(element.l);
        }
        if (env->ExceptionCheck()) {
          c->throwJavaExceptionFromJs(env);
        }
        return result;
      };
    }
  }

  if (typeName == "java.lang.String") {
    return [](Context* c, JNIEnv* env, jvalue v) {
      if (!v.l) return JS_NULL;
      const auto s = env->GetStringUTFChars(static_cast<jstring>(v.l), nullptr);
      auto jsString = JS_NewString(c->jsContext, s);
      env->ReleaseStringUTFChars(static_cast<jstring>(v.l), s);
      return jsString;
    };
  } else if (typeName == "java.lang.Double" || (typeName == "double" && boxed)) {
    return [](Context* c, JNIEnv* env, jvalue v) {
      return v.l != nullptr
             ? JS_NewFloat64(c->jsContext, env->CallDoubleMethod(v.l, c->doubleGetValue))
             : JS_NULL;
    };
  } else if (typeName == "java.lang.Integer" || (typeName == "int" && boxed)) {
    return [](Context* c, JNIEnv* env, jvalue v) {
      return v.l != nullptr
             ? JS_NewInt32(c->jsContext, env->CallIntMethod(v.l, c->integerGetValue))
             : JS_NULL;
    };
  } else if (typeName == "java.lang.Boolean" || (typeName == "boolean" && boxed)) {
    return [](Context* c, JNIEnv* env, jvalue v) {
      return v.l != nullptr
             ? JS_NewBool(c->jsContext, env->CallBooleanMethod(v.l, c->booleanGetValue))
             : JS_NULL;
    };
  } else if (typeName == "double") {
    return [](Context* c, JNIEnv* env, jvalue v) {
      return JS_NewFloat64(c->jsContext, v.d);
    };
  } else if (typeName == "int") {
    return [](Context* c, JNIEnv* env, jvalue v) {
      return JS_NewInt32(c->jsContext, v.i);
    };
  } else if (typeName == "boolean") {
    return [](Context* c, JNIEnv* env, jvalue v) {
      return JS_NewBool(c->jsContext, v.z);
    };
  } else if (typeName == "java.lang.Object") {
    return [](Context* c, JNIEnv* env, jvalue v) {
      if (!v.l) return JS_NULL;
      return c->getJavaToJsConverter(env, env->GetObjectClass(v.l), true)(c, env, v);
    };
  } else if (typeName == "void") {
    return [](Context*, JNIEnv* env, jvalue) {
      return JS_UNDEFINED;
    };
  } else {
    // Throw an exception for unsupported argument type.
    throwJavaException(env, "java/lang/IllegalArgumentException", "Unsupported Java type %s",
                       typeName.c_str());
    return [typeName](Context* context, JNIEnv* env, jvalue) {
      return JS_ThrowTypeError(context->jsContext, "Unsupported Java type %s", typeName.c_str());
    };
  }
}

Context::JavaScriptToJava
Context::getJsToJavaConverter(JNIEnv* env, jclass type, bool boxed) {
  const auto typeName = getName(env, type);
  if (!typeName.empty() && typeName[0] == '[') {
    // type is an array.
    const jmethodID method = env->GetMethodID(env->GetObjectClass(type),
                                              "getComponentType",
                                              "()Ljava/lang/Class;");
    auto elementType = static_cast<jclass>(env->CallObjectMethod(type, method));
    const auto elementTypeName = getName(env, elementType);
    if (elementTypeName == "double") {
      return [](Context* c, JNIEnv* env, const JSValueConst& v) {
        jvalue result;
        if (JS_IsNull(v) || JS_IsUndefined(v)) {
          result.l = nullptr;
        } else if (JS_IsException(v)) {
          result.l = nullptr;
          c->throwJsException(env, v);
        } else {
          int length = 0;
          auto jsLength = JS_GetPropertyStr(c->jsContext, v, "length");
          if (JS_ToInt32(c->jsContext, &length, jsLength)) {
            result.l = nullptr;
            c->throwJsException(env, jsLength);
          } else {
            result.l = env->NewDoubleArray(length);
            for (int i = 0; i < length && !env->ExceptionCheck(); i++) {
              double element;
              auto jsElement = JS_GetPropertyUint32(c->jsContext, v, i);
              if (!JS_IsNumber(jsElement)) {
                const auto str = JS_ToCString(c->jsContext, jsElement);
                throwJavaException(env, "java/lang/IllegalArgumentException",
                                   "Cannot convert value %s to double", str);
                JS_FreeCString(c->jsContext, str);
              } else if (JS_ToFloat64(c->jsContext, &element, jsElement)) {
                c->throwJsException(env, jsElement);
              } else {
                env->SetDoubleArrayRegion(static_cast<jdoubleArray>(result.l), i, 1, &element);
              }
              JS_FreeValue(c->jsContext, jsElement);
            }
          }
          JS_FreeValue(c->jsContext, jsLength);
        }
        return result;
      };
    } else if (elementTypeName == "int") {
      return [](Context* c, JNIEnv* env, const JSValueConst& v) {
        jvalue result;
        if (JS_IsNull(v) || JS_IsUndefined(v)) {
          result.l = nullptr;
        } else if (JS_IsException(v)) {
          result.l = nullptr;
          c->throwJsException(env, v);
        } else {
          int length = 0;
          auto jsLength = JS_GetPropertyStr(c->jsContext, v, "length");
          if (JS_ToInt32(c->jsContext, &length, jsLength)) {
            result.l = nullptr;
            c->throwJsException(env, jsLength);
          } else {
            result.l = env->NewIntArray(length);
            for (int i = 0; i < length && !env->ExceptionCheck(); i++) {
              int element;
              auto jsElement = JS_GetPropertyUint32(c->jsContext, v, i);
              if (JS_VALUE_GET_TAG(jsElement) != JS_TAG_INT) {
                const auto str = JS_ToCString(c->jsContext, jsElement);
                throwJavaException(env, "java/lang/IllegalArgumentException",
                                   "Cannot convert value %s to int", str);
                JS_FreeCString(c->jsContext, str);
              } else if (JS_ToInt32(c->jsContext, &element, jsElement)) {
                c->throwJsException(env, jsElement);
              } else {
                env->SetIntArrayRegion(static_cast<jintArray>(result.l), i, 1, &element);
              }
              JS_FreeValue(c->jsContext, jsElement);
            }
          }
          JS_FreeValue(c->jsContext, jsLength);
        }
        return result;
      };
    } else if (elementTypeName == "boolean") {
      return [](Context* c, JNIEnv* env, const JSValueConst& v) {
        jvalue result;
        if (JS_IsNull(v) || JS_IsUndefined(v)) {
          result.l = nullptr;
        } else if (JS_IsException(v)) {
          result.l = nullptr;
          c->throwJsException(env, v);
        } else {
          int length = 0;
          auto jsLength = JS_GetPropertyStr(c->jsContext, v, "length");
          if (JS_ToInt32(c->jsContext, &length, jsLength)) {
            result.l = nullptr;
            c->throwJsException(env, jsLength);
          } else {
            result.l = env->NewBooleanArray(length);
            for (int i = 0; i < length && !env->ExceptionCheck(); i++) {
              auto jsElement = JS_GetPropertyUint32(c->jsContext, v, i);
              if (!JS_IsBool(jsElement)) {
                const auto str = JS_ToCString(c->jsContext, jsElement);
                throwJavaException(env, "java/lang/IllegalArgumentException",
                                   "Cannot convert value %s to boolean", str);
                JS_FreeCString(c->jsContext, str);
              } else {
                int r = JS_ToBool(c->jsContext, jsElement);
                if (r < 0) {
                  c->throwJsException(env, jsElement);
                } else {
                  jboolean element = r != 0;
                  env->SetBooleanArrayRegion(static_cast<jbooleanArray>(result.l), i, 1, &element);
                }
              }
              JS_FreeValue(c->jsContext, jsElement);
            }
          }
          JS_FreeValue(c->jsContext, jsLength);
        }
        return result;
      };
    } else {
      auto converter = getJsToJavaConverter(env, elementType, true);
      auto elementTypeGlobalRef = getGlobalRef(env, elementType);
      return [converter, elementTypeGlobalRef](Context* c, JNIEnv* env,
                                               const JSValueConst& v) {
        jvalue result;
        if (JS_IsNull(v) || JS_IsUndefined(v)) {
          result.l = nullptr;
        } else if (JS_IsException(v)) {
          result.l = nullptr;
          c->throwJsException(env, v);
        } else {
          int length = 0;
          auto jsLength = JS_GetPropertyStr(c->jsContext, v, "length");
          if (JS_ToInt32(c->jsContext, &length, jsLength)) {
            result.l = nullptr;
            c->throwJsException(env, jsLength);
          } else {
            result.l = env->NewObjectArray(length, elementTypeGlobalRef, nullptr);
            for (int i = 0; i < length && !env->ExceptionCheck(); i++) {
              auto jsElement = JS_GetPropertyUint32(c->jsContext, v, i);
              jvalue element = converter(c, env, jsElement);
              JS_FreeValue(c->jsContext, jsElement);
              if (env->ExceptionCheck()) break;
              env->SetObjectArrayElement(static_cast<jobjectArray>(result.l), i, element.l);
            }
          }
          JS_FreeValue(c->jsContext, jsLength);
        }
        return result;
      };
    }
  }

  if (typeName == "java.lang.String") {
    return [](Context* c, JNIEnv* env, const JSValueConst& v) {
      jvalue result;
      if (JS_IsNull(v) || JS_IsUndefined(v)) {
        result.l = nullptr;
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(env, v);
      } else if (!JS_IsString(v)) {
        result.l = nullptr;
        const auto str = JS_ToCString(c->jsContext, v);
        throwJavaException(env, "java/lang/IllegalArgumentException",
                           "Cannot convert value %s to String", str);
        JS_FreeCString(c->jsContext, str);
      } else {
        const auto str = JS_ToCString(c->jsContext, v);
        result.l = env->NewStringUTF(str);
        JS_FreeCString(c->jsContext, str);
      }
      return result;
    };
  } else if (typeName == "java.lang.Double" || (typeName == "double" && boxed)) {
    return [](Context* c, JNIEnv* env, const JSValueConst& v) {
      jvalue result;
      if (JS_IsNull(v) || JS_IsUndefined(v)) {
        result.l = nullptr;
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(env, v);
      } else {
        if (JS_ToFloat64(c->jsContext, &result.d, v)) {
          c->throwJsException(env, v);
          result.l = nullptr;
        } else {
          result.l = env->CallStaticObjectMethodA(c->doubleClass, c->doubleValueOf, &result);
        }
      }
      return result;
    };
  } else if (typeName == "java.lang.Integer" || (typeName == "int" && boxed)) {
    return [](Context* c, JNIEnv* env, const JSValueConst& v) {
      jvalue result;
      if (JS_IsNull(v) || JS_IsUndefined(v)) {
        result.l = nullptr;
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(env, v);
      } else {
        if (JS_ToInt32(c->jsContext, &result.i, v)) {
          c->throwJsException(env, v);
          result.l = nullptr;
        } else {
          result.l = env->CallStaticObjectMethodA(c->integerClass, c->integerValueOf, &result);
        }
      }
      return result;
    };
  } else if (typeName == "java.lang.Boolean" || (typeName == "boolean" && boxed)) {
    return [](Context* c, JNIEnv* env, const JSValueConst& v) {
      jvalue result;
      if (JS_IsNull(v) || JS_IsUndefined(v)) {
        result.l = nullptr;
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(env, v);
      } else {
        int r = JS_ToBool(c->jsContext, v);
        if (r < 0) {
          c->throwJsException(env, v);
          result.l = nullptr;
        } else {
          result.z = r != 0;
          result.l = env->CallStaticObjectMethodA(c->booleanClass, c->booleanValueOf, &result);
        }
      }
      return result;
    };
  } else if (typeName == "double") {
    return [](Context* c, JNIEnv* env, const JSValueConst& v) {
      jvalue result;
      if (!JS_IsNumber(v)) {
        result.l = nullptr;
        const auto str = JS_ToCString(c->jsContext, v);
        throwJavaException(env, "java/lang/IllegalArgumentException",
                           "Cannot convert value %s to double", str);
        JS_FreeCString(c->jsContext, str);
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(env, v);
      } else if (JS_ToFloat64(c->jsContext, &result.d, v)) {
        c->throwJsException(env, v);
      }
      return result;
    };
  } else if (typeName == "int") {
    return [](Context* c, JNIEnv* env, const JSValueConst& v) {
      jvalue result;
      if (JS_VALUE_GET_TAG(v) != JS_TAG_INT) {
        result.l = nullptr;
        const auto str = JS_ToCString(c->jsContext, v);
        throwJavaException(env, "java/lang/IllegalArgumentException",
                           "Cannot convert value %s to int", str);
        JS_FreeCString(c->jsContext, str);
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(env, v);
      } else if (JS_ToInt32(c->jsContext, &result.i, v)) {
        c->throwJsException(env, v);
      }
      return result;
    };
  } else if (typeName == "boolean") {
    return [](Context* c, JNIEnv* env, const JSValueConst& v) {
      jvalue result;
      if (!JS_IsBool(v)) {
        result.l = nullptr;
        const auto str = JS_ToCString(c->jsContext, v);
        throwJavaException(env, "java/lang/IllegalArgumentException",
                           "Cannot convert value %s to boolean", str);
        JS_FreeCString(c->jsContext, str);
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(env, v);
      } else {
        int r = JS_ToBool(c->jsContext, v);
        if (r < 0) {
          c->throwJsException(env, v);
        }
        result.z = r != 0;
      }
      return result;
    };
  } else if (typeName == "java.lang.Object") {
    return [](Context* c, JNIEnv* env, const JSValueConst& v) {
      jvalue result;
      result.l = c->toJavaObject(env, v);
      return result;
    };
  } else if (typeName == "void") {
    return [](Context* c, JNIEnv* env, const JSValueConst& v) {
      jvalue result;
      result.l = nullptr;
      if (JS_IsException(v)) {
        c->throwJsException(env, v);
      }
      return result;
    };
  } else {
    // Throw an exception for unsupported argument type.
    throwJavaException(env, "java/lang/IllegalArgumentException", "Unsupported Java type %s",
                       typeName.c_str());
    return [typeName](Context* context, JNIEnv* env, const JSValueConst&) {
      throwJavaException(env, "java/lang/IllegalArgumentException",
                         "Unsupported Java type %s", typeName.c_str());
      jvalue result;
      result.l = nullptr;
      return result;
    };
  }
}

void Context::throwJsException(JNIEnv* env, const JSValue& value) const {
  JSValue exceptionValue = JS_GetException(jsContext);

  JSValue messageValue = JS_GetPropertyStr(jsContext, exceptionValue, "message");
  JSValue stackValue = JS_GetPropertyStr(jsContext, exceptionValue, "stack");

  // If the JS does a `throw 2;`, there won't be a message property.
  const char* message = JS_ToCString(jsContext,
                                     JS_IsUndefined(messageValue) ? exceptionValue : messageValue);
  JS_FreeValue(jsContext, messageValue);

  const char* stack = JS_ToCString(jsContext, stackValue);
  JS_FreeValue(jsContext, stackValue);
  JS_FreeValue(jsContext, exceptionValue);

  jthrowable cause = static_cast<jthrowable>(JS_GetContextOpaque(jsContext));
  JS_SetContextOpaque(jsContext, nullptr);

  jobject exception;
  if (cause) {
    exception = env->NewLocalRef(cause);
    env->DeleteGlobalRef(cause);

    // add the JavaScript stack to this exception.
    const jmethodID addJavaScriptStack =
        env->GetStaticMethodID(quickJsExceptionClass,
                               "addJavaScriptStack",
                               "(Ljava/lang/Throwable;Ljava/lang/String;)V");
    env->CallStaticVoidMethod(quickJsExceptionClass, addJavaScriptStack, exception,
                              env->NewStringUTF(stack));
  } else {
    exception = env->NewObject(quickJsExceptionClass,
                               quickJsExceptionConstructor,
                               env->NewStringUTF(message),
                               env->NewStringUTF(stack));
  }

  JS_FreeCString(jsContext, stack);
  JS_FreeCString(jsContext, message);

  env->Throw(static_cast<jthrowable>(exception));
}

JSValue Context::throwJavaExceptionFromJs(JNIEnv* env) const {
  assert(env->ExceptionCheck()); // There must be something to throw.
  assert(JS_GetContextOpaque(jsContext) == nullptr); // There can't be a pending thrown exception.
  auto exception = env->ExceptionOccurred();
  env->ExceptionClear();
  JS_SetContextOpaque(jsContext, env->NewGlobalRef(exception));
  return JS_ThrowInternalError(jsContext, "Java Exception");
}

JNIEnv* Context::getEnv() const {
  JNIEnv* env = nullptr;
  javaVm->GetEnv(reinterpret_cast<void**>(&env), jniVersion);
  if (env) {
    return env;
  }

  javaVm->AttachCurrentThread(
#ifdef __ANDROID__
      &env,
#else
      reinterpret_cast<void**>(&env),
#endif
      nullptr);
  if (env) {
    thread_local JniThreadDetacher detacher(javaVm);
  }
  return env;
}

JSValue
Context::jsCall(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv, int magic) {
  auto context = reinterpret_cast<const Context*>(JS_GetRuntimeOpaque(JS_GetRuntime(ctx)));
  if (context) {
    auto proxy = reinterpret_cast<const JavaObjectProxy*>(JS_GetOpaque(this_val, context->jsClassId));
    if (proxy) {
      return proxy->call(magic, argc, argv);
    }
  }
  return JS_ThrowReferenceError(ctx, "Null Java Proxy");
}

jclass Context::getGlobalRef(JNIEnv* env, jclass clazz) {
  auto name = getName(env, clazz);
  auto i = globalReferences.find(name);
  if (i != globalReferences.end()) {
    return i->second;
  }

  auto globalRef = static_cast<jclass>(env->NewGlobalRef(clazz));
  globalReferences[name] = globalRef;
  return globalRef;
}
