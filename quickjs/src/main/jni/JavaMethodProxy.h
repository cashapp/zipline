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
#ifndef QUICKJS_ANDROID_JAVAMETHODPROXY_H
#define QUICKJS_ANDROID_JAVAMETHODPROXY_H

#include <jni.h>
#include <functional>
#include <vector>
#include <string>
#include "quickjs/quickjs.h"

class Context;

class JavaMethodProxy {
public:
  JavaMethodProxy(Context*, JNIEnv*, jobject method);
  ~JavaMethodProxy();

  JSValue invoke(Context* context, jobject javaThis, int argc, JSValueConst *argv) const;

  uint32_t numArgs() const {
    return argumentLoaders.size();
  }

  const std::string name;

private:
  std::vector<std::function<jvalue(Context*, JNIEnv*, const JSValueConst&)>> argumentLoaders;
  std::function<JSValueConst(Context*, JNIEnv*, jobject, const jvalue*)> javaCaller;
  bool isVarArgs;
};

#endif //QUICKJS_ANDROID_JAVAMETHODPROXY_H
