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
#ifndef DUKTAPE_ANDROID_JAVAMETHOD_H
#define DUKTAPE_ANDROID_JAVAMETHOD_H

#include <vector>
#include <functional>
#include <jni.h>
#include "duktape.h"

class JavaMethod {
public:
  JavaMethod(JNIEnv* env, jobject method);

  /**
   * Invokes this method using {@code javaThis}, with the arguments on the stack given in {@ctx}.
   * Returns the number of results pushed to the Duktape stack, or a negative status code.
   */
  duk_ret_t invoke(duk_context *ctx, JNIEnv *env, jobject javaThis) const;

  /**
   * Defines a functor to use to pop a value off the Duktape stack and convert it to the required
   * Java type.
   */
  typedef std::function<jvalue(duk_context*, JNIEnv*)> JavaValuePopper;
  /**
   * Defines a functor to use to push a value onto the Duktape stack, unboxing and converting it to
   * the required JavaScript type.
   */
  typedef std::function<duk_ret_t(duk_context*, JNIEnv*, jvalue)> JavaValuePusher;

  /**
   * Defines a functor to invoke the correct JNI method that will return the required Java type,
   * convert the return value to a JavaScript type and push it to the Duktape stack.  Returns the
   * number of entries pushed to the stack.
   * If the Java method throws an exception, the functor will throw a Duktape error with the
   * exception inside.
   */
  typedef std::function<duk_ret_t(duk_context*, JNIEnv*, jobject, jvalue*)> MethodBody;

  // TODO: these static methods and functor types belong in their own class.

  static JavaValuePopper getValuePopper(JNIEnv *env, jobject typeObject, bool forceBoxed = false);
  static JavaValuePusher getValuePusher(JNIEnv* env, jclass type, bool forceBoxed = false);

private:
  std::vector<JavaValuePopper> m_argumentLoaders;
  MethodBody m_methodBody;
};

#endif //DUKTAPE_ANDROID_JAVAMETHOD_H
