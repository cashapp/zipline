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

JavaObjectProxy::JavaObjectProxy(Context* c, const char* name, jobject object,
                                 jobjectArray methods, JSValueConst proxy)
    : context(c), name(name), javaThis(c->env->NewGlobalRef(object)) {
  auto env = context->env;
  const auto numMethods = env->GetArrayLength(methods);
  functions = new JSCFunctionListEntry[numMethods];
  for (int i = 0; i < numMethods && !env->ExceptionCheck(); i++) {
    auto method = env->GetObjectArrayElement(methods, i);
    if (env->ExceptionCheck()) {
      break;
    }
    const auto methodName = getName(env, method);
    functions[i] = JS_CFUNC_MAGIC_DEF(strdup(methodName.c_str()), 0, Context::jsCall,
                                      static_cast<short>(i));
    proxies.emplace_back(context, method);
    env->DeleteLocalRef(method);
  }

  if (!env->ExceptionCheck()) {
    JS_SetPropertyFunctionList(context->jsContext, proxy, functions, numMethods);
  }
}

JavaObjectProxy::~JavaObjectProxy() {
  for (int i = 0; i < proxies.size(); i++) {
    free(const_cast<char*>(functions[i].name));
  }
  delete[] functions;
  context->env->DeleteGlobalRef(javaThis);
}

JSValue
JavaObjectProxy::call(int magic, int argc, JSValueConst* argv) const {
  if (magic < proxies.size()) {
    return proxies[magic].invoke(context, javaThis, argc, argv);
  } else {
    return JS_ThrowInternalError(context->jsContext, "Function not found");
  }
}
