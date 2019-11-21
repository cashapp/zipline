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
#include "JsObjectProxy.h"
#include "quickjs/quickjs.h"
#include "Context.h"
#include "ExceptionThrowers.h"
#include "JsMethodProxy.h"

JsObjectProxy::JsObjectProxy(const char *name)
    : name(name) {
}

JsObjectProxy::~JsObjectProxy() {
  for (auto method : methods) {
    delete method;
  }
}

jobject JsObjectProxy::call(Context *context, JNIEnv* env, jobject method, jobjectArray args) const {
  JSValue global = JS_GetGlobalObject(context->jsContext);
  JSValue thisPointer = JS_GetPropertyStr(context->jsContext, global, name.c_str());

  const JsMethodProxy *methodProxy = nullptr;
  for (auto m : methods) {
    if (env->FromReflectedMethod(method) == m->methodId) {
      methodProxy = m;
      break;
    }
  }

  jobject result;
  if (methodProxy) {
    result = methodProxy->call(context, env, thisPointer, args);
  } else {
    const jclass methodClass = env->GetObjectClass(method);
    const jmethodID getName = env->GetMethodID(methodClass, "getName", "()Ljava/lang/String;");
    jstring methodName = static_cast<jstring>(env->CallObjectMethod(method, getName));
    const char *methodNameStr = env->GetStringUTFChars(methodName, 0);
    throwJsExceptionFmt(env, context, "Could not find method %s.%s", name.c_str(), methodNameStr);
    env->ReleaseStringUTFChars(methodName, methodNameStr);
    result = nullptr;
  }
  JS_FreeValue(context->jsContext, thisPointer);
  JS_FreeValue(context->jsContext, global);
  return result;
}


