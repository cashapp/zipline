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
#include "../duktape/duktape.h"

class JavaType;
class JavaTypeMap;

class JavaMethod {
public:
  JavaMethod(JavaTypeMap&, JNIEnv* env, jobject method);

  /**
   * Invokes this method using {@code javaThis}, with the arguments on the stack given in {@ctx}.
   * Returns the number of results pushed to the Duktape stack, or a negative status code.
   */
  duk_ret_t invoke(duk_context *ctx, JNIEnv *env, jobject javaThis) const;

private:
  std::vector<const JavaType*> m_argumentLoaders;
  std::function<duk_ret_t(duk_context*, JNIEnv*, jobject, jvalue*)> m_methodBody;
  bool m_isVarArgs;
};

#endif //DUKTAPE_ANDROID_JAVAMETHOD_H
