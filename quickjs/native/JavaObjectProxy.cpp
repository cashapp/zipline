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
#include "JavaObjectProxy.h"
#include "Context.h"
#include "ExceptionThrowers.h"
#include "JavaMethodProxy.h"

JavaObjectProxy::JavaObjectProxy(Context* c, JNIEnv* env, const char* name, jobject object,
                                 jobjectArray methods, JSValueConst proxy)
    : context(c), name(name), javaThis(env->NewGlobalRef(object)) {
  const auto numMethods = env->GetArrayLength(methods);
  functions.resize(numMethods);
  auto f = functions.data();
  for (int i = 0; i < numMethods && !env->ExceptionCheck(); i++) {
    auto method = env->GetObjectArrayElement(methods, i);
    if (env->ExceptionCheck()) {
      break;
    }
    proxies.emplace_back(context, env, method);
    const auto& proxyMethod = proxies.back();
    f[i] = JS_CFUNC_MAGIC_DEF(strdup(proxyMethod.name.c_str()), static_cast<uint8_t>(proxyMethod.numArgs()),
                              Context::jsCall, static_cast<short>(i));
    env->DeleteLocalRef(method);
  }

  if (!env->ExceptionCheck()) {
    JS_SetPropertyFunctionList(context->jsContext, proxy, functions.data(), functions.size());
  }
}

JavaObjectProxy::~JavaObjectProxy() {
  context->getEnv()->DeleteGlobalRef(javaThis);
  for (auto& f : functions) {
    delete [] f.name;
  }
}

JSValue
JavaObjectProxy::call(int magic, int argc, JSValueConst* argv) const {
  if (magic < proxies.size()) {
    return proxies[magic].invoke(context, javaThis, argc, argv);
  } else {
    return JS_ThrowInternalError(context->jsContext, "Function not found");
  }
}
