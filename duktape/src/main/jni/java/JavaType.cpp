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
#include "JavaType.h"
#include "JString.h"
#include "JavaExceptions.h"
#include "GlobalRef.h"

jvalue JavaType::callMethod(duk_context* ctx, JNIEnv *env, jmethodID methodId, jobject javaThis,
                            jvalue* args) const {
  jobject returnValue = env->CallObjectMethodA(javaThis, methodId, args);
  checkRethrowDuktapeError(env, ctx);
  jvalue result;
  result.l = returnValue;
  return result;
}

namespace  {

struct Void : public JavaType {
  Void(bool pushUndefined)
      : m_pushUndefined(pushUndefined) {
  }

  jvalue pop(duk_context* ctx, JNIEnv*) const override {
    duk_pop(ctx);
    jvalue value;
    value.l = nullptr;
    return value;
  }

  duk_ret_t push(duk_context* ctx, JNIEnv*, const jvalue&) const override {
    if (m_pushUndefined) {
      duk_push_undefined(ctx);
      return 1;
    } else {
      return 0;
    }
  }

  jvalue callMethod(duk_context* ctx, JNIEnv* env, jmethodID methodId, jobject javaThis,
                    jvalue *args) const override {
    env->CallVoidMethodA(javaThis, methodId, args);
    checkRethrowDuktapeError(env, ctx);
    jvalue result;
    result.l = nullptr;
    return result;
  }

  const bool m_pushUndefined;
};

struct String : public JavaType {
  jvalue pop(duk_context* ctx, JNIEnv* env) const override {
    jvalue value;
    // Check if the caller passed in a null string.
    value.l = duk_get_type(ctx, -1) != DUK_TYPE_NULL
              ? env->NewStringUTF(duk_require_string(ctx, -1))
              : nullptr;
    duk_pop(ctx);
    return value;
  }

  duk_ret_t push(duk_context* ctx, JNIEnv* env, const jvalue& value) const override {
    if (value.l != nullptr) {
      const JString result(env, static_cast<jstring>(value.l));
      duk_push_string(ctx, result);
    } else {
      duk_push_null(ctx);
    }
    return 1;
  }
};

struct Primitive : public JavaType {
public:
  Primitive(JNIEnv* env, jclass boxedClass)
      : m_boxedClassRef(env, boxedClass) {
  }

  virtual const char* getUnboxSignature() const = 0;
  virtual const char* getUnboxMethodName() const = 0;
  virtual const char* getBoxSignature() const = 0;
  virtual const char* getBoxMethodName() const { return "valueOf"; }

  bool isPrimitive() const override { return true; }
  jclass boxedClass() const { return static_cast<jclass>(m_boxedClassRef.get()); }

private:
  const GlobalRef m_boxedClassRef;
};

struct Boolean : public Primitive {
  Boolean(JNIEnv* env, jclass boxedBoolean)
      : Primitive(env, boxedBoolean) {
  }

  jvalue pop(duk_context* ctx, JNIEnv*) const override {
    jvalue value;
    value.z = duk_require_boolean(ctx, -1);
    duk_pop(ctx);
    return value;
  }

  duk_ret_t push(duk_context* ctx, JNIEnv*, const jvalue& value) const override {
    duk_push_boolean(ctx, value.z == JNI_TRUE);
    return 1;
  }

  jvalue callMethod(duk_context* ctx, JNIEnv* env, jmethodID methodId, jobject javaThis,
                    jvalue *args) const override {
    jboolean returnValue = env->CallBooleanMethodA(javaThis, methodId, args);
    checkRethrowDuktapeError(env, ctx);
    jvalue result;
    result.z = returnValue;
    return result;
  }

  const char* getUnboxSignature() const override {
    return "()Z";
  }
  const char* getUnboxMethodName() const override {
    return "booleanValue";
  }
  const char* getBoxSignature() const override {
    return "(Z)Ljava/lang/Boolean;";
  }
};

struct Integer : public Primitive {
  Integer(JNIEnv* env, jclass integerClass)
      : Primitive(env, integerClass) {
  }

  jvalue pop(duk_context* ctx, JNIEnv*) const override {
    jvalue value;
    value.i = duk_require_int(ctx, -1);
    duk_pop(ctx);
    return value;
  }

  duk_ret_t push(duk_context* ctx, JNIEnv*, const jvalue& value) const override {
    duk_push_int(ctx, value.i);
    return 1;
  }

  jvalue callMethod(duk_context* ctx, JNIEnv* env, jmethodID methodId, jobject javaThis,
                    jvalue *args) const override {
    jint returnValue = env->CallIntMethodA(javaThis, methodId, args);
    checkRethrowDuktapeError(env, ctx);
    jvalue result;
    result.i = returnValue;
    return result;
  }

  const char* getUnboxSignature() const override {
    return "()I";
  }
  const char* getUnboxMethodName() const override {
    return "intValue";
  }
  const char* getBoxSignature() const override {
    return "(I)Ljava/lang/Integer;";
  }
};

struct Double : public Primitive {
  Double(JNIEnv* env, jclass boxedDouble)
      : Primitive(env, boxedDouble) {
  }

  jvalue pop(duk_context* ctx, JNIEnv*) const override {
    jvalue value;
    value.d = duk_require_number(ctx, -1);
    duk_pop(ctx);
    return value;
  }

  duk_ret_t push(duk_context* ctx, JNIEnv*, const jvalue& value) const override {
    duk_push_number(ctx, value.d);
    return 1;
  }

  jvalue callMethod(duk_context* ctx, JNIEnv* env, jmethodID methodId, jobject javaThis,
                    jvalue *args) const override {
    jdouble returnValue = env->CallDoubleMethodA(javaThis, methodId, args);
    checkRethrowDuktapeError(env, ctx);
    jvalue result;
    result.d = returnValue;
    return result;
  }

  const char* getUnboxSignature() const override {
    return "()D";
  }
  const char* getUnboxMethodName() const override {
    return "doubleValue";
  }
  const char* getBoxSignature() const override {
    return "(D)Ljava/lang/Double;";
  }
};

class BoxedPrimitive : public JavaType {
public:
  BoxedPrimitive(JNIEnv* env, const Primitive& primitive)
      : m_primitive(primitive)
      , m_unbox(env->GetMethodID(primitive.boxedClass(),
                                 primitive.getUnboxMethodName(),
                                 primitive.getUnboxSignature()))
      , m_box(env->GetStaticMethodID(primitive.boxedClass(),
                                     primitive.getBoxMethodName(),
                                     primitive.getBoxSignature())) {
  }

  jvalue pop(duk_context* ctx, JNIEnv* env) const override {
    jvalue value;
    if (duk_get_type(ctx, -1) != DUK_TYPE_NULL) {
      value = m_primitive.pop(ctx, env);
      value.l = env->CallStaticObjectMethodA(m_primitive.boxedClass(), m_box, &value);
      checkRethrowDuktapeError(env, ctx);
    } else {
      duk_pop(ctx);
      value.l = nullptr;
    }
    return value;
  }

  duk_ret_t push(duk_context* ctx, JNIEnv* env, const jvalue& value) const override {
    if (value.l != nullptr) {
      const jvalue unboxedValue = m_primitive.callMethod(ctx, env, m_unbox, value.l, nullptr);
      return m_primitive.push(ctx, env, unboxedValue);
    } else {
      duk_push_null(ctx);
      return 1;
    }
  }

private:
  const Primitive& m_primitive;
  const jmethodID m_unbox;
  const jmethodID m_box;
};

struct Object : public JavaType {
  Object(const JavaType& boxedBoolean, const JavaType& boxedDouble, JavaTypeMap& typeMap)
      : m_boxedBoolean(boxedBoolean)
      , m_boxedDouble(boxedDouble)
      , m_typeMap(typeMap) {
  }

  jvalue pop(duk_context* ctx, JNIEnv* env) const override {
    jvalue value;
    switch (duk_get_type(ctx, -1)) {
      case DUK_TYPE_NULL:
      case DUK_TYPE_UNDEFINED:
        value.l = nullptr;
        duk_pop(ctx);
        break;

      case DUK_TYPE_BOOLEAN:
        value = m_boxedBoolean.pop(ctx, env);
        break;

      case DUK_TYPE_NUMBER:
        value = m_boxedDouble.pop(ctx, env);
        break;

      case DUK_TYPE_STRING:
        value.l = env->NewStringUTF(duk_require_string(ctx, -1));
        duk_pop(ctx);
        break;

      default:
        duk_error(ctx, DUK_RET_TYPE_ERROR,
                  "Cannot marshal %s to Java", duk_safe_to_string(ctx, -1));
        break;
    }
    return value;
  }

  duk_ret_t push(duk_context* ctx, JNIEnv* env, const jvalue& value) const override {
    if (value.l == nullptr) {
      duk_push_null(ctx);
      return 1;
    }
    return m_typeMap.get(env, env->GetObjectClass(value.l))->push(ctx, env, value);
  }

  const JavaType& m_boxedBoolean;
  const JavaType& m_boxedDouble;
  JavaTypeMap& m_typeMap;
};

/**
 * Loads the (primitive) TYPE member of {@code boxedClassName}.
 * For example, given java/lang/Integer, this function will return int.class.
 */
jclass getPrimitiveType(JNIEnv* env, jclass boxedClass) {
  const jfieldID typeField = env->GetStaticFieldID(boxedClass, "TYPE", "Ljava/lang/Class;");
  return static_cast<jclass>(env->GetStaticObjectField(boxedClass, typeField));
}

/** Calls toString() on the given object and returns a copy of the result. */
std::string toString(JNIEnv* env, jobject object) {
  const jmethodID method =
      env->GetMethodID(env->GetObjectClass(object), "toString", "()Ljava/lang/String;");
  const JString methodName(env, static_cast<jstring>(env->CallObjectMethod(object, method)));
  return methodName.str();
}

} // anonymous namespace

JavaTypeMap::~JavaTypeMap() {
  for (auto entry : m_types) {
    delete entry.second;
  }
}

const JavaType* JavaTypeMap::get(JNIEnv* env, jclass c) {
  if (m_types.empty()) {
    // Load up the map with the types we support.
    const jclass voidClass = env->FindClass("java/lang/Void");
    const jclass vClass = getPrimitiveType(env, voidClass);
    m_types.emplace(std::make_pair(toString(env, vClass), new Void(false)));
    m_types.emplace(std::make_pair(toString(env, voidClass), new Void(true)));

    const jclass stringClass = env->FindClass("java/lang/String");
    m_types.emplace(std::make_pair(toString(env, stringClass), new String()));

    const jclass booleanClass = env->FindClass("java/lang/Boolean");
    const jclass bClass = getPrimitiveType(env, booleanClass);
    const auto boolType = new Boolean(env, booleanClass);
    m_types.emplace(std::make_pair(toString(env, bClass), boolType));
    const auto boxedBooleanType = new BoxedPrimitive(env, *boolType);
    m_types.emplace(std::make_pair(toString(env, booleanClass), boxedBooleanType));

    const jclass integerClass = env->FindClass("java/lang/Integer");
    const jclass iClass = getPrimitiveType(env, integerClass);
    const auto intType = new Integer(env, integerClass);
    m_types.emplace(std::make_pair(toString(env, iClass), intType));
    m_types.emplace(std::make_pair(toString(env, integerClass), new BoxedPrimitive(env, *intType)));

    const jclass doubleClass = env->FindClass("java/lang/Double");
    const jclass dClass = getPrimitiveType(env, doubleClass);
    const auto doubleType = new Double(env, doubleClass);
    m_types.emplace(std::make_pair(toString(env, dClass), doubleType));
    const auto boxedDoubleType = new BoxedPrimitive(env, *doubleType);
    m_types.emplace(std::make_pair(toString(env, doubleClass), boxedDoubleType));

    const jclass objectClass = env->FindClass("java/lang/Object");
    m_types.emplace(std::make_pair(toString(env, objectClass),
                                   new Object(*boxedBooleanType, *boxedDoubleType, *this)));
  }

  const auto name = toString(env, c);
  const auto I = m_types.find(name);
  if (I != m_types.end()) {
    return I->second;
  }

  throw std::invalid_argument("Unsupported Java type " + name);
}

const JavaType* JavaTypeMap::getBoxed(JNIEnv* env, jclass c) {
  const JavaType* javaType = get(env, c);
  if (!javaType->isPrimitive()) {
    return javaType;
  }

  const Primitive* primitive = static_cast<const Primitive*>(javaType);
  return get(env, primitive->boxedClass());
}
