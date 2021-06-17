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
#ifndef QUICKJS_ANDROID_JSOBJECTPROXY_H
#define QUICKJS_ANDROID_JSOBJECTPROXY_H

#include <jni.h>
#include <vector>
#include <string>

class Context;
class JsMethodProxy;

class JsObjectProxy {
public:
  JsObjectProxy(const char *name);
  ~JsObjectProxy();

  jobject call(Context* context, JNIEnv*, jobject method, jobjectArray args) const;

  const std::string name;
  std::vector<JsMethodProxy*> methods;
};

#endif //QUICKJS_ANDROID_JSOBJECTPROXY_H
