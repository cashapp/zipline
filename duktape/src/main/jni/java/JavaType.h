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
#ifndef DUKTAPE_ANDROID_JAVAVALUE_H
#define DUKTAPE_ANDROID_JAVAVALUE_H

#include <map>
#include <jni.h>
#include "../duktape/duktape.h"

/**
 * Represents an instance of a Java class.  Handles pushing/popping values of the represented type
 * to/from the Duktape stack with appropriate conversions and boxing/unboxing.
 */
class JavaType {
public:
  virtual ~JavaType() = default;

  /**
   * Pops a {@code jvalue} from the Duktape stack in {@code ctx}. if {@code inScript} is true,
   * caller is inside a JavaScript execution so JavaScript exceptions can be used. If false, type
   * errors will throw C++ exceptions.
   */
  virtual jvalue pop(duk_context* ctx, JNIEnv* env, bool inScript) const = 0;
  /**
   * Pushes {@code value} to the Duktape stack in {@code ctx}. Returns the number of entries pushed.
   */
  virtual duk_ret_t push(duk_context* ctx, JNIEnv*, const jvalue& value) const = 0;
  /**
   * Calls the given Java method with {@code javaThis} and {@code args}.  Returns the result from
   * the method.  The Duktape context is only modified to propagate exceptions thrown by the JVM.
   */
  virtual jvalue callMethod(duk_context*, JNIEnv*, jmethodID, jobject javaThis, jvalue* args) const;
  /**
   * Return true if this is a primitive (int, boolean, etc.), false if not (String, Integer, etc.).
   */
  virtual bool isPrimitive() const { return false; }
  /** Return true if this type is a java.lang.Integer. */
  virtual bool isInteger() const { return false; }
};

/** Manages the {@code JavaType} instances for a particular {@code DuktapeContext}. */
class JavaTypeMap {
public:
  ~JavaTypeMap();
  /** Get the JavaType to use to marshal instances of {@code javaClass}. */
  const JavaType* get(JNIEnv*, jclass javaClass);
  /** Get the JavaType to use to marshal instances of {@code javaClass}, force boxed primitives. */
  const JavaType* getBoxed(JNIEnv*, jclass javaClass);
  /** Get the JavaType that represents Object. */
  const JavaType* getObjectType(JNIEnv*);

private:
  const JavaType* find(JNIEnv*, const std::string&);
  std::map<std::string, const JavaType*> m_types;
};

/** Calls toString() on the given object and returns a copy of the result. */
std::string toString(JNIEnv* env, jobject object);

#endif //DUKTAPE_ANDROID_JAVAVALUE_H
