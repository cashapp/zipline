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
#include "JavaMethodProxy.h"
#include <algorithm>
#include <string>
#include "Context.h"
#include "ExceptionThrowers.h"

JavaMethodProxy::JavaMethodProxy(Context* context, jobject method) {
  auto env = context->env;
  auto methodId = env->FromReflectedMethod(method);
  jclass methodClass = env->GetObjectClass(method);

  const jmethodID isVarArgsMethod = env->GetMethodID(methodClass, "isVarArgs", "()Z");
  isVarArgs = env->CallBooleanMethod(method, isVarArgsMethod);

  const jmethodID getParameterTypes = env->GetMethodID(methodClass, "getParameterTypes",
                                                       "()[Ljava/lang/Class;");
  jobjectArray parameterTypes =
      static_cast<jobjectArray>(env->CallObjectMethod(method, getParameterTypes));
  const jsize numArgs = env->GetArrayLength(parameterTypes);
  for (jsize i = 0; i < numArgs && !env->ExceptionCheck(); ++i) {
    auto parameterType = env->GetObjectArrayElement(parameterTypes, i);
    argumentLoaders.push_back(
        context->getJsToJavaConverter(static_cast<jclass>(parameterType), false));
    env->DeleteLocalRef(parameterType);
  }

  if (!env->ExceptionCheck()) {
    const jmethodID getReturnType =
        env->GetMethodID(methodClass, "getReturnType", "()Ljava/lang/Class;");
    auto returnTypeObject = env->CallObjectMethod(method, getReturnType);
    auto resultLoader = context->getJavaToJsConverter(static_cast<jclass>(returnTypeObject), false);

    if (!env->ExceptionCheck()) {
      auto returnTypeName = getName(env, returnTypeObject);
      if (returnTypeName == "void") {
        javaCaller = [methodId, resultLoader](const Context* context, jobject javaThis,
                                              const jvalue* args) {
          context->env->CallVoidMethodA(javaThis, methodId, args);
          if (!context->env->ExceptionCheck()) {
            return JS_UNDEFINED;
          } else {
            // TODO: steal code from checkRethrowDuktapeError and queueJavaExceptionForDuktapeError
            return JS_ThrowInternalError(context->jsContext, "Java Exception");
          }
        };
      } else if (returnTypeName == "boolean") {
        javaCaller = [methodId, resultLoader](const Context* context, jobject javaThis,
                                              const jvalue* args) {
          jvalue result;
          result.z = context->env->CallBooleanMethodA(javaThis, methodId, args);
          JSValue jsResult;
          if (!context->env->ExceptionCheck()) {
            jsResult = resultLoader(context, result);
          } else {
            // TODO: steal code from checkRethrowDuktapeError and queueJavaExceptionForDuktapeError
            jsResult = JS_ThrowInternalError(context->jsContext, "Java Exception");
          }
          return jsResult;
        };
      } else if (returnTypeName == "int") {
        javaCaller = [methodId, resultLoader](const Context* context, jobject javaThis,
                                              const jvalue* args) {
          jvalue result;
          result.i = context->env->CallIntMethodA(javaThis, methodId, args);
          JSValue jsResult;
          if (!context->env->ExceptionCheck()) {
            jsResult = resultLoader(context, result);
          } else {
            // TODO: steal code from checkRethrowDuktapeError and queueJavaExceptionForDuktapeError
            jsResult = JS_ThrowInternalError(context->jsContext, "Java Exception");
          }
          return jsResult;
        };
      } else if (returnTypeName == "double") {
        javaCaller = [methodId, resultLoader](const Context* context, jobject javaThis,
                                              const jvalue* args) {
          jvalue result;
          result.d = context->env->CallDoubleMethodA(javaThis, methodId, args);
          JSValue jsResult;
          if (!context->env->ExceptionCheck()) {
            jsResult = resultLoader(context, result);
          } else {
            // TODO: steal code from checkRethrowDuktapeError and queueJavaExceptionForDuktapeError
            jsResult = JS_ThrowInternalError(context->jsContext, "Java Exception");
          }
          return jsResult;
        };
      } else {
        javaCaller = [methodId, resultLoader](const Context* context, jobject javaThis,
                                              const jvalue* args) {
          jvalue result;
          result.l = context->env->CallObjectMethodA(javaThis, methodId, args);
          JSValue jsResult;
          if (!context->env->ExceptionCheck()) {
            jsResult = resultLoader(context, result);
          } else {
            // TODO: steal code from checkRethrowDuktapeError and queueJavaExceptionForDuktapeError
            jsResult = JS_ThrowInternalError(context->jsContext, "Java Exception");
          }
          return jsResult;
        };
      }
    }

    env->DeleteLocalRef(returnTypeObject);
  }
  env->DeleteLocalRef(parameterTypes);
  env->DeleteLocalRef(methodClass);
}

JavaMethodProxy::~JavaMethodProxy() {

}

JSValue
JavaMethodProxy::invoke(Context* context, jobject javaThis, int argc, JSValueConst* argv) const {
  const auto minArgs = isVarArgs ? argumentLoaders.size() - 1 : argumentLoaders.size();
  if (minArgs > argc || (!isVarArgs && minArgs < argc)) {
    return JS_ThrowRangeError(context->jsContext, "Wrong number of arguments");
  }

  auto env = context->env;
  env->PushLocalFrame(argc + 1);
  jvalue args[argumentLoaders.size()];
  for (int i = 0; i < minArgs; i++) {
    args[i] = argumentLoaders[i](context, argv[i]);
    if (env->ExceptionCheck()) {
      env->PopLocalFrame(nullptr);
      // TODO: steal code from checkRethrowDuktapeError and queueJavaExceptionForDuktapeError
      return JS_ThrowInternalError(context->jsContext, "Java Exception");
    }
  }
  if (isVarArgs) {
    auto varArgs = JS_NewArray(context->jsContext);
    for (int i = minArgs; i < argc; i++) {
      JS_SetPropertyUint32(context->jsContext, varArgs, i - minArgs,
                           JS_DupValue(context->jsContext, argv[i]));
    }
    args[minArgs] = argumentLoaders.back()(context, varArgs);
    JS_FreeValue(context->jsContext, varArgs);
    if (env->ExceptionCheck()) {
      env->PopLocalFrame(nullptr);
      // TODO: steal code from checkRethrowDuktapeError and queueJavaExceptionForDuktapeError
      return JS_ThrowInternalError(context->jsContext, "Java Exception");
    }
  }

  auto result = javaCaller(context, javaThis, args);
  env->PopLocalFrame(nullptr);
  return result;
}
