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

JSClassID classId = 0;

void jsFinalize(JSRuntime*, JSValue val) {
  delete reinterpret_cast<JavaObjectProxy*>(JS_GetOpaque(val, classId));
}

} // anonymous namespace

Context::Context(JNIEnv* env)
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

JsObjectProxy* Context::getObjectProxy(jstring name, jobjectArray methods) {
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
        jsObjectProxy->methods.push_back(new JsMethodProxy(this, methodNameStr, method));
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
    throwJsException(obj);
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

void Context::setObjectProxy(jstring name, jobject object, jobjectArray methods) {
  auto global = JS_GetGlobalObject(jsContext);

  const char* nameStr = env->GetStringUTFChars(name, 0);

  const auto objName = JS_NewAtom(jsContext, nameStr);
  if (!JS_HasProperty(jsContext, global, objName)) {
    if (classId == 0) {
      JS_NewClassID(&classId);
      JSClassDef classDef;
      memset(&classDef, 0, sizeof(JSClassDef));
      classDef.class_name = "QuickJsAndroidProxy";
      classDef.finalizer = jsFinalize;
      if (JS_NewClass(jsRuntime, classId, &classDef)) {
        classId = 0;
        throwJavaException(env, "java/lang/NullPointerException",
                           "Failed to allocate JavaScript proxy class");
      }
    }
    if (classId != 0) {
      auto proxy = JS_NewObjectClass(jsContext, classId);
      if (JS_IsException(proxy) || JS_SetProperty(jsContext, global, objName, proxy) <= 0) {
        throwJsException(proxy);
      } else {
        JS_SetOpaque(proxy, new JavaObjectProxy(this, nameStr, object, methods, proxy));
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

jobject Context::toJavaObject(const JSValueConst& value) const {
  jobject result;
  switch (JS_VALUE_GET_TAG(value)) {
    case JS_TAG_EXCEPTION: {
      throwJsException(value);
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
  const char* sourceCode = env->GetStringUTFChars(source, 0);
  const char* fileName = env->GetStringUTFChars(file, 0);

  JSValue evalValue = JS_Eval(jsContext, sourceCode, strlen(sourceCode), fileName, 0);

  env->ReleaseStringUTFChars(source, sourceCode);
  env->ReleaseStringUTFChars(file, fileName);

  jobject result = toJavaObject(evalValue);

  JS_FreeValue(jsContext, evalValue);

  return result;
}

Context::JavaToJavaScript Context::getJavaToJsConverter(jclass type, bool boxed) const {
  const auto typeName = getName(env, type);
  if (!typeName.empty() && typeName[0] == '[') {
    // type is an array.
    const jmethodID method = env->GetMethodID(env->GetObjectClass(type),
                                              "getComponentType",
                                              "()Ljava/lang/Class;");
    auto elementType = static_cast<jclass>(env->CallObjectMethod(type, method));
    const auto elementTypeName = getName(env, elementType);
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
      auto converter = getJavaToJsConverter(elementType, true);
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
      return c->getJavaToJsConverter(c->env->GetObjectClass(v.l), true)(c, v);
    };
  } else if (typeName == "void") {
    return [](const Context*, jvalue) {
      return JS_UNDEFINED;
    };
  } else {
    // Throw an exception for unsupported argument type.
    throwJavaException(env, "java/lang/IllegalArgumentException", "Unsupported Java type %s",
                       typeName.c_str());
    return [typeName](const Context* context, jvalue) {
      return JS_ThrowTypeError(context->jsContext, "Unsupported Java type %s", typeName.c_str());
    };
  }
}

Context::JavaScriptToJava Context::getJsToJavaConverter(jclass type, bool boxed) const {
  const auto typeName = getName(env, type);
  if (!typeName.empty() && typeName[0] == '[') {
    // type is an array.
    const jmethodID method = env->GetMethodID(env->GetObjectClass(type),
                                              "getComponentType",
                                              "()Ljava/lang/Class;");
    auto elementType = static_cast<jclass>(env->CallObjectMethod(type, method));
    const auto elementTypeName = getName(env, elementType);
    if (elementTypeName == "double") {
      return [](const Context* c, const JSValueConst& v) {
        jvalue result;
        if (JS_IsNull(v) || JS_IsUndefined(v)) {
          result.l = nullptr;
        } else if (JS_IsException(v)) {
          result.l = nullptr;
          c->throwJsException(v);
        } else {
          int length = 0;
          auto jsLength = JS_GetPropertyStr(c->jsContext, v, "length");
          if (JS_ToInt32(c->jsContext, &length, jsLength)) {
            result.l = nullptr;
            c->throwJsException(jsLength);
          } else {
            result.l = c->env->NewDoubleArray(length);
            for (int i = 0; i < length && !c->env->ExceptionCheck(); i++) {
              double element;
              auto jsElement = JS_GetPropertyUint32(c->jsContext, v, i);
              if (!JS_IsNumber(jsElement)) {
                const auto str = JS_ToCString(c->jsContext, jsElement);
                throwJavaException(c->env, "java/lang/IllegalArgumentException",
                                   "Cannot convert value %s to double", str);
                JS_FreeCString(c->jsContext, str);
              } else if (JS_ToFloat64(c->jsContext, &element, jsElement)) {
                c->throwJsException(jsElement);
              } else {
                c->env->SetDoubleArrayRegion(static_cast<jdoubleArray>(result.l), i, 1, &element);
              }
              JS_FreeValue(c->jsContext, jsElement);
            }
          }
          JS_FreeValue(c->jsContext, jsLength);
        }
        return result;
      };
    } else if (elementTypeName == "int") {
      return [](const Context* c, const JSValueConst& v) {
        jvalue result;
        if (JS_IsNull(v) || JS_IsUndefined(v)) {
          result.l = nullptr;
        } else if (JS_IsException(v)) {
          result.l = nullptr;
          c->throwJsException(v);
        } else {
          int length = 0;
          auto jsLength = JS_GetPropertyStr(c->jsContext, v, "length");
          if (JS_ToInt32(c->jsContext, &length, jsLength)) {
            result.l = nullptr;
            c->throwJsException(jsLength);
          } else {
            result.l = c->env->NewIntArray(length);
            for (int i = 0; i < length && !c->env->ExceptionCheck(); i++) {
              int element;
              auto jsElement = JS_GetPropertyUint32(c->jsContext, v, i);
              if (!JS_IsInteger(jsElement)) {
                const auto str = JS_ToCString(c->jsContext, jsElement);
                throwJavaException(c->env, "java/lang/IllegalArgumentException",
                                   "Cannot convert value %s to int", str);
                JS_FreeCString(c->jsContext, str);
              } else if (JS_ToInt32(c->jsContext, &element, jsElement)) {
                c->throwJsException(jsElement);
              } else {
                c->env->SetIntArrayRegion(static_cast<jintArray>(result.l), i, 1, &element);
              }
              JS_FreeValue(c->jsContext, jsElement);
            }
          }
          JS_FreeValue(c->jsContext, jsLength);
        }
        return result;
      };
    } else if (elementTypeName == "boolean") {
      return [](const Context* c, const JSValueConst& v) {
        jvalue result;
        if (JS_IsNull(v) || JS_IsUndefined(v)) {
          result.l = nullptr;
        } else if (JS_IsException(v)) {
          result.l = nullptr;
          c->throwJsException(v);
        } else {
          int length = 0;
          auto jsLength = JS_GetPropertyStr(c->jsContext, v, "length");
          if (JS_ToInt32(c->jsContext, &length, jsLength)) {
            result.l = nullptr;
            c->throwJsException(jsLength);
          } else {
            result.l = c->env->NewBooleanArray(length);
            for (int i = 0; i < length && !c->env->ExceptionCheck(); i++) {
              auto jsElement = JS_GetPropertyUint32(c->jsContext, v, i);
              if (!JS_IsBool(jsElement)) {
                const auto str = JS_ToCString(c->jsContext, jsElement);
                throwJavaException(c->env, "java/lang/IllegalArgumentException",
                                   "Cannot convert value %s to boolean", str);
                JS_FreeCString(c->jsContext, str);
              } else {
                int r = JS_ToBool(c->jsContext, jsElement);
                if (r < 0) {
                  c->throwJsException(jsElement);
                } else {
                  jboolean element = r != 0;
                  c->env->SetBooleanArrayRegion(static_cast<jbooleanArray>(result.l), i, 1,
                                                &element);
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
      auto converter = getJsToJavaConverter(elementType, true);
      // TODO: don't leak this
      auto elementTypeGlobalRef(static_cast<jclass>(env->NewGlobalRef(elementType)));
      return [converter, elementTypeGlobalRef](const Context* c, const JSValueConst& v) {
        jvalue result;
        if (JS_IsNull(v) || JS_IsUndefined(v)) {
          result.l = nullptr;
        } else if (JS_IsException(v)) {
          result.l = nullptr;
          c->throwJsException(v);
        } else {
          int length = 0;
          auto jsLength = JS_GetPropertyStr(c->jsContext, v, "length");
          if (JS_ToInt32(c->jsContext, &length, jsLength)) {
            result.l = nullptr;
            c->throwJsException(jsLength);
          } else {
            result.l = c->env->NewObjectArray(length, elementTypeGlobalRef, nullptr);
            for (int i = 0; i < length && !c->env->ExceptionCheck(); i++) {
              auto jsElement = JS_GetPropertyUint32(c->jsContext, v, i);
              jvalue element = converter(c, jsElement);
              JS_FreeValue(c->jsContext, jsElement);
              if (c->env->ExceptionCheck()) break;
              c->env->SetObjectArrayElement(static_cast<jobjectArray>(result.l), i, element.l);
            }
          }
          JS_FreeValue(c->jsContext, jsLength);
        }
        return result;
      };
    }
  }

  if (typeName == "java.lang.String") {
    return [](const Context* c, const JSValueConst& v) {
      jvalue result;
      if (JS_IsNull(v) || JS_IsUndefined(v)) {
        result.l = nullptr;
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(v);
      } else if (!JS_IsString(v)) {
        result.l = nullptr;
        const auto str = JS_ToCString(c->jsContext, v);
        throwJavaException(c->env, "java/lang/IllegalArgumentException",
                           "Cannot convert value %s to String", str);
        JS_FreeCString(c->jsContext, str);
      } else {
        const auto str = JS_ToCString(c->jsContext, v);
        result.l = c->env->NewStringUTF(str);
        JS_FreeCString(c->jsContext, str);
      }
      return result;
    };
  } else if (typeName == "java.lang.Double" || (typeName == "double" && boxed)) {
    return [](const Context* c, const JSValueConst& v) {
      jvalue result;
      if (JS_IsNull(v) || JS_IsUndefined(v)) {
        result.l = nullptr;
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(v);
      } else {
        if (JS_ToFloat64(c->jsContext, &result.d, v)) {
          c->throwJsException(v);
          result.l = nullptr;
        } else {
          result.l = c->env->CallStaticObjectMethodA(c->doubleClass, c->doubleValueOf, &result);
        }
      }
      return result;
    };
  } else if (typeName == "java.lang.Integer" || (typeName == "int" && boxed)) {
    return [](const Context* c, const JSValueConst& v) {
      jvalue result;
      if (JS_IsNull(v) || JS_IsUndefined(v)) {
        result.l = nullptr;
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(v);
      } else {
        if (JS_ToInt32(c->jsContext, &result.i, v)) {
          c->throwJsException(v);
          result.l = nullptr;
        } else {
          result.l = c->env->CallStaticObjectMethodA(c->integerClass, c->integerValueOf, &result);
        }
      }
      return result;
    };
  } else if (typeName == "java.lang.Boolean" || (typeName == "boolean" && boxed)) {
    return [](const Context* c, const JSValueConst& v) {
      jvalue result;
      if (JS_IsNull(v) || JS_IsUndefined(v)) {
        result.l = nullptr;
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(v);
      } else {
        int r = JS_ToBool(c->jsContext, v);
        if (r < 0) {
          c->throwJsException(v);
          result.l = nullptr;
        } else {
          result.z = r != 0;
          result.l = c->env->CallStaticObjectMethodA(c->booleanClass, c->booleanValueOf, &result);
        }
      }
      return result;
    };
  } else if (typeName == "double") {
    return [](const Context* c, const JSValueConst& v) {
      jvalue result;
      if (!JS_IsNumber(v)) {
        result.l = nullptr;
        const auto str = JS_ToCString(c->jsContext, v);
        throwJavaException(c->env, "java/lang/IllegalArgumentException",
                           "Cannot convert value %s to double", str);
        JS_FreeCString(c->jsContext, str);
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(v);
      } else if (JS_ToFloat64(c->jsContext, &result.d, v)) {
        c->throwJsException(v);
      }
      return result;
    };
  } else if (typeName == "int") {
    return [](const Context* c, const JSValueConst& v) {
      jvalue result;
      if (!JS_IsInteger(v)) {
        result.l = nullptr;
        const auto str = JS_ToCString(c->jsContext, v);
        throwJavaException(c->env, "java/lang/IllegalArgumentException",
                           "Cannot convert value %s to int", str);
        JS_FreeCString(c->jsContext, str);
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(v);
      } else if (JS_ToInt32(c->jsContext, &result.i, v)) {
        c->throwJsException(v);
      }
      return result;
    };
  } else if (typeName == "boolean") {
    return [](const Context* c, const JSValueConst& v) {
      jvalue result;
      if (!JS_IsBool(v)) {
        result.l = nullptr;
        const auto str = JS_ToCString(c->jsContext, v);
        throwJavaException(c->env, "java/lang/IllegalArgumentException",
                           "Cannot convert value %s to boolean", str);
        JS_FreeCString(c->jsContext, str);
      } else if (JS_IsException(v)) {
        result.l = nullptr;
        c->throwJsException(v);
      } else {
        int r = JS_ToBool(c->jsContext, v);
        if (r < 0) {
          c->throwJsException(v);
        }
        result.z = r != 0;
      }
      return result;
    };
  } else if (typeName == "java.lang.Object") {
    return [](const Context* c, const JSValueConst& v) {
      jvalue result;
      result.l = c->toJavaObject(v);
      return result;
    };
  } else if (typeName == "void") {
    return [](const Context* c, const JSValueConst& v) {
      jvalue result;
      result.l = nullptr;
      if (JS_IsException(v)) {
        c->throwJsException(v);
      }
      return result;
    };
  } else {
    // Throw an exception for unsupported argument type.
    throwJavaException(env, "java/lang/IllegalArgumentException", "Unsupported Java type %s",
                       typeName.c_str());
    return [typeName](const Context* context, const JSValueConst&) {
      throwJavaException(context->env, "java/lang/IllegalArgumentException",
                         "Unsupported Java type %s", typeName.c_str());
      jvalue result;
      result.l = nullptr;
      return result;
    };
  }
}

void Context::throwJsException(const JSValue& value) const {
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
  jobject exception = env->NewObject(quickJsExceptionClass,
                                     quickJsExceptionConstructor,
                                     env->NewStringUTF(message),
                                     env->NewStringUTF(stack));
  JS_FreeCString(jsContext, stack);
  JS_FreeCString(jsContext, message);

  env->Throw(static_cast<jthrowable>(exception));
}

JSValue
Context::jsCall(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv, int magic) {
  auto proxy = reinterpret_cast<const JavaObjectProxy*>(JS_GetOpaque(this_val, classId));
  if (proxy) {
    return proxy->call(magic, argc, argv);
  }
  return JS_ThrowReferenceError(ctx, "Null Java Proxy");
}
