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
#ifndef QUICKJS_ANDROID_JAVACALLCHANNEL_H
#define QUICKJS_ANDROID_JAVACALLCHANNEL_H

#include <jni.h>
#include <string>
#include <vector>
#include "quickjs/quickjs.h"

class Context;

class JavaCallChannel {
public:
  JavaCallChannel(Context*, JNIEnv*, const char* name, jobject object, JSValueConst proxy);

  ~JavaCallChannel();

  static JSValue jsServiceNamesArray(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv);
  static JSValue jsInvoke(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv);
  static JSValue jsInvokeSuspending(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv);
  static JSValue jsDisconnect(JSContext* ctx, JSValueConst this_val, int argc, JSValueConst* argv);

private:
  Context* context;
  const std::string name;
  jobject javaThis;
  jclass callChannelClass;
  jmethodID callChannelServiceNamesArray;
  jmethodID callChannelInvoke;
  jmethodID callChannelInvokeSuspending;
  jmethodID callChannelDisconnect;
  std::vector<JSCFunctionListEntry> functions;
};

#endif //QUICKJS_ANDROID_JAVACALLCHANNEL_H
