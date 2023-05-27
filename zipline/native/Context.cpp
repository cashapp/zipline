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
#include "Context.h"
#include <cstring>
#include <memory>
#include <assert.h>
#include "OutboundCallChannel.h"
#include "InboundCallChannel.h"
#include "ExceptionThrowers.h"
#include "common/context-no-eval.h"
#include "common/finalization-registry.h"
#include "quickjs/quickjs.h"

/**
 * This signature satisfies the JSInterruptHandler typedef. It is always installed but only does
 * work if a Kotlin InterruptHandler is configured.
 */
int jsInterruptHandlerPoll(JSRuntime* jsRuntime, void *opaque) {
  auto context = reinterpret_cast<Context*>(opaque);
  auto interruptHandler = context->interruptHandler;
  if (interruptHandler == nullptr) return 0;

  JS_SetInterruptHandler(context->jsRuntime, NULL, NULL); // Suppress re-enter.
  auto env = context->getEnv();
  const jboolean halt = env->CallBooleanMethod(interruptHandler, context->interruptHandlerPoll);
  JS_SetInterruptHandler(context->jsRuntime, &jsInterruptHandlerPoll, context); // Restore handler.
  // TODO: propagate the interrupt handler's exceptions through JS.
  return halt;
}

namespace {

void jsFinalizeOutboundCallChannel(JSRuntime* jsRuntime, JSValue val) {
  auto context = reinterpret_cast<const Context*>(JS_GetRuntimeOpaque(jsRuntime));
  if (context) {
    delete reinterpret_cast<OutboundCallChannel*>(
        JS_GetOpaque(val, context->outboundCallChannelClassId));
  }
}

struct JniThreadDetacher {
  JavaVM& javaVm;

  JniThreadDetacher(JavaVM* javaVm) : javaVm(*javaVm) {
  }

  ~JniThreadDetacher() {
    javaVm.DetachCurrentThread();
  }
};

} // anonymous namespace

Context::Context(JNIEnv* env)
    : jniVersion(env->GetVersion()),
      jsRuntime(JS_NewRuntime()),
      jsContext(JS_NewContextNoEval(jsRuntime)),
      jsContextForCompiling(JS_NewContext(jsRuntime)),
      outboundCallChannelClassId(0),
      lengthAtom(JS_NewAtom(jsContext, "length")),
      callAtom(JS_NewAtom(jsContext, "call")),
      disconnectAtom(JS_NewAtom(jsContext, "disconnect")),
      booleanClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Boolean")))),
      integerClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Integer")))),
      doubleClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Double")))),
      objectClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Object")))),
      stringClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/String")))),
      stringUtf8(static_cast<jstring>(env->NewGlobalRef(env->NewStringUTF("UTF-8")))),
      quickJsExceptionClass(static_cast<jclass>(env->NewGlobalRef(
          env->FindClass("app/cash/zipline/QuickJsException")))),
      booleanValueOf(env->GetStaticMethodID(booleanClass, "valueOf", "(Z)Ljava/lang/Boolean;")),
      integerValueOf(env->GetStaticMethodID(integerClass, "valueOf", "(I)Ljava/lang/Integer;")),
      doubleValueOf(env->GetStaticMethodID(doubleClass, "valueOf", "(D)Ljava/lang/Double;")),
      stringGetBytes(env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B")),
      stringConstructor(env->GetMethodID(stringClass, "<init>", "([BLjava/lang/String;)V")),
      quickJsExceptionConstructor(env->GetMethodID(quickJsExceptionClass, "<init>",
                                                   "(Ljava/lang/String;Ljava/lang/String;)V")),
      interruptHandlerClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("app/cash/zipline/InterruptHandler")))),
      interruptHandlerPoll(env->GetMethodID(interruptHandlerClass, "poll", "()Z")),
      interruptHandler(nullptr) {
  env->GetJavaVM(&javaVm);
  JS_SetRuntimeOpaque(jsRuntime, this);
  JS_SetInterruptHandler(jsRuntime, &jsInterruptHandlerPoll, this);

  if (installFinalizationRegistry(jsContext, jsContextForCompiling) < 0) {
    throwJavaException(env, "java/lang/IllegalStateException",
                       "Failed to install FinalizationRegistry");
  }
}

Context::~Context() {
  for (auto callChannel : callChannels) {
    delete callChannel;
  }
  auto env = getEnv();
  for (auto refs : globalReferences) {
    env->DeleteGlobalRef(refs.second);
  }
  if (interruptHandler != nullptr) {
    env->DeleteGlobalRef(interruptHandler);
  }
  env->DeleteGlobalRef(interruptHandlerClass);
  env->DeleteGlobalRef(quickJsExceptionClass);
  env->DeleteGlobalRef(stringUtf8);
  env->DeleteGlobalRef(stringClass);
  env->DeleteGlobalRef(objectClass);
  env->DeleteGlobalRef(doubleClass);
  env->DeleteGlobalRef(integerClass);
  env->DeleteGlobalRef(booleanClass);
  JS_FreeAtom(jsContext, lengthAtom);
  JS_FreeAtom(jsContext, callAtom);
  JS_FreeAtom(jsContext, disconnectAtom);
  JS_FreeContext(jsContext);
  JS_FreeContext(jsContextForCompiling);
  JS_FreeRuntime(jsRuntime);
}

jobject Context::execute(JNIEnv* env, jbyteArray byteCode) {
  const auto buffer = env->GetByteArrayElements(byteCode, nullptr);
  const auto bufferLength = env->GetArrayLength(byteCode);
  const auto flags = JS_READ_OBJ_BYTECODE | JS_READ_OBJ_REFERENCE | JS_EVAL_FLAG_STRICT;
  auto obj = JS_ReadObject(jsContext, reinterpret_cast<const uint8_t*>(buffer), bufferLength, flags);
  env->ReleaseByteArrayElements(byteCode, buffer, JNI_ABORT);

  if (JS_IsException(obj)) {
    throwJsException(env, obj);
    return nullptr;
  }

  if (JS_ResolveModule(jsContext, obj)) {
    throwJsExceptionFmt(env, this, "Failed to resolve JS module");
    return nullptr;
  }

  auto val = JS_EvalFunction(jsContext, obj);
  jobject result;
  if (!JS_IsException(val)) {
    result = toJavaObject(env, val, false);
  } else {
    result = nullptr;
    throwJsException(env, val);
  }
  JS_FreeValue(jsContext, val);

  return result;
}

jbyteArray Context::compile(JNIEnv* env, jstring source, jstring file) {
  const auto sourceCode = env->GetStringUTFChars(source, 0);
  const auto fileName = env->GetStringUTFChars(file, 0);

  auto compiled = JS_Eval(jsContextForCompiling, sourceCode, strlen(sourceCode), fileName, JS_EVAL_FLAG_COMPILE_ONLY | JS_EVAL_FLAG_STRICT);

  env->ReleaseStringUTFChars(file, fileName);
  env->ReleaseStringUTFChars(source, sourceCode);

  if (JS_IsException(compiled)) {
    // TODO: figure out how to get the failing line number into the exception.
    throwJsException(env, compiled);
    JS_FreeValue(jsContextForCompiling, compiled);
    return nullptr;
  }

  size_t bufferLength = 0;
  auto buffer = JS_WriteObject(jsContextForCompiling, &bufferLength, compiled, JS_WRITE_OBJ_BYTECODE | JS_WRITE_OBJ_REFERENCE);

  auto result = buffer && bufferLength > 0 ? env->NewByteArray(bufferLength) : nullptr;
  if (result) {
    env->SetByteArrayRegion(result, 0, bufferLength, reinterpret_cast<const jbyte*>(buffer));
  } else {
    throwJsException(env, compiled);
  }

  JS_FreeValue(jsContextForCompiling, compiled);
  js_free(jsContextForCompiling, buffer);

  return result;
}

void Context::setInterruptHandler(JNIEnv* env, jobject newInterruptHandler) {
  jobject oldInterruptHandler = interruptHandler;
  if (oldInterruptHandler != nullptr) {
    env->DeleteGlobalRef(oldInterruptHandler);
  }
  interruptHandler = env->NewGlobalRef(newInterruptHandler);
}

jobject Context::memoryUsage(JNIEnv* env) {
  auto memoryUsageClass = env->FindClass("app/cash/zipline/MemoryUsage");
  if (!memoryUsageClass) {
    // Should be impossible. If R8 removed the type then this function can never be invoked.
    return nullptr;
  }

  auto memoryUsageConstructor = env->GetMethodID(memoryUsageClass, "<init>", "(JJJJJJJJJJJJJJJJJJJJJJJJJJ)V");
  if (!memoryUsageConstructor) {
    // Should be impossible. If R8 removed the type then this function can never be invoked.
    return nullptr;
  }

  JSMemoryUsage jsMemoryUsage;
  JS_ComputeMemoryUsage(jsRuntime, &jsMemoryUsage);

  return static_cast<jstring>(env->NewObject(
    memoryUsageClass,
    memoryUsageConstructor,
    jsMemoryUsage.malloc_count,
    jsMemoryUsage.malloc_size,
    jsMemoryUsage.malloc_limit,
    jsMemoryUsage.memory_used_count,
    jsMemoryUsage.memory_used_size,
    jsMemoryUsage.atom_count,
    jsMemoryUsage.atom_size,
    jsMemoryUsage.str_count,
    jsMemoryUsage.str_size,
    jsMemoryUsage.obj_count,
    jsMemoryUsage.obj_size,
    jsMemoryUsage.prop_count,
    jsMemoryUsage.prop_size,
    jsMemoryUsage.shape_count,
    jsMemoryUsage.shape_size,
    jsMemoryUsage.js_func_count,
    jsMemoryUsage.js_func_size,
    jsMemoryUsage.js_func_code_size,
    jsMemoryUsage.js_func_pc2line_count,
    jsMemoryUsage.js_func_pc2line_size,
    jsMemoryUsage.c_func_count,
    jsMemoryUsage.array_count,
    jsMemoryUsage.fast_array_count,
    jsMemoryUsage.fast_array_elements,
    jsMemoryUsage.binary_object_count,
    jsMemoryUsage.binary_object_size
  ));
}

void Context::setMemoryLimit(JNIEnv* env, jlong limit) {
  JS_SetMemoryLimit(jsRuntime, limit);
}

void Context::setGcThreshold(JNIEnv* env, jlong gcThreshold) {
  JS_SetGCThreshold(jsRuntime, gcThreshold);
}

void Context::setMaxStackSize(JNIEnv* env, jlong stackSize) {
  JS_SetMaxStackSize(jsRuntime, stackSize);
}

void Context::gc(JNIEnv* env) {
  JS_RunGC(jsRuntime);
}

InboundCallChannel* Context::getInboundCallChannel(JNIEnv* env, jstring name) {
  JSValue global = JS_GetGlobalObject(jsContext);

  const char* nameStr = env->GetStringUTFChars(name, 0);

  JSValue obj = JS_GetPropertyStr(jsContext, global, nameStr);

  InboundCallChannel* inboundCallChannel = nullptr;
  if (JS_IsObject(obj)) {
    inboundCallChannel = new InboundCallChannel(jsContext, nameStr);
    if (!env->ExceptionCheck()) {
      callChannels.push_back(inboundCallChannel);
    } else {
      delete inboundCallChannel;
      inboundCallChannel = nullptr;
    }
  } else if (JS_IsException(obj)) {
    throwJsException(env, obj);
  } else {
    const char* msg = JS_IsUndefined(obj)
                      ? "A global JavaScript object called %s was not found. Try confirming that Zipline.get() has been called."
                      : "JavaScript global called %s is not an object";
    throwJavaException(env, "java/lang/IllegalStateException", msg, nameStr);
  }

  JS_FreeValue(jsContext, obj);

  env->ReleaseStringUTFChars(name, nameStr);
  JS_FreeValue(jsContext, global);

  return inboundCallChannel;
}

void Context::setOutboundCallChannel(JNIEnv* env, jstring name, jobject callChannel) {
  auto global = JS_GetGlobalObject(jsContext);

  const char* nameStr = env->GetStringUTFChars(name, 0);

  const auto objName = JS_NewAtom(jsContext, nameStr);
  if (!JS_HasProperty(jsContext, global, objName)) {
    if (outboundCallChannelClassId == 0) {
      JS_NewClassID(&outboundCallChannelClassId);
      JSClassDef classDef;
      memset(&classDef, 0, sizeof(JSClassDef));
      classDef.class_name = "OutboundCallChannel";
      classDef.finalizer = jsFinalizeOutboundCallChannel;
      if (JS_NewClass(jsRuntime, outboundCallChannelClassId, &classDef) < 0) {
        outboundCallChannelClassId = 0;
        throwJavaException(env, "java/lang/NullPointerException",
                           "Failed to allocate JavaScript OutboundCallChannel class");
      }
    }
    if (outboundCallChannelClassId != 0) {
      auto jsOutboundCallChannel = JS_NewObjectClass(jsContext, outboundCallChannelClassId);
      if (JS_IsException(jsOutboundCallChannel) || JS_SetProperty(jsContext, global, objName, jsOutboundCallChannel) <= 0) {
        throwJsException(env, jsOutboundCallChannel);
      } else {
        std::unique_ptr<OutboundCallChannel> javaObject(new OutboundCallChannel(this, env, nameStr, callChannel, jsOutboundCallChannel));
        if (!env->ExceptionCheck()) {
          JS_SetOpaque(jsOutboundCallChannel, javaObject.release());
        }
      }
    }
  } else {
    throwJavaException(env, "java/lang/IllegalArgumentException",
                       "A global object called %s already exists", nameStr);
  }
  JS_FreeAtom(jsContext, objName);
  env->ReleaseStringUTFChars(name, nameStr);
  JS_FreeValue(jsContext, global);
}

jobject
Context::toJavaObject(JNIEnv* env, const JSValueConst& value, bool throwOnUnsupportedType) {
  jobject result;
  switch (JS_VALUE_GET_NORM_TAG(value)) {
    case JS_TAG_EXCEPTION: {
      throwJsException(env, value);
      result = nullptr;
      break;
    }

    case JS_TAG_STRING: {
      result = toJavaString(env, value);
      break;
    }

    case JS_TAG_BOOL: {
      jvalue v;
      v.z = static_cast<jboolean>(JS_VALUE_GET_BOOL(value));
      result = env->CallStaticObjectMethodA(booleanClass, booleanValueOf, &v);
      break;
    }

    case JS_TAG_INT: {
      jvalue v;
      v.j = static_cast<jint>(JS_VALUE_GET_INT(value));
      result = env->CallStaticObjectMethodA(integerClass, integerValueOf, &v);
      break;
    }

    case JS_TAG_FLOAT64: {
      jvalue v;
      v.d = static_cast<jdouble>(JS_VALUE_GET_FLOAT64(value));
      result = env->CallStaticObjectMethodA(doubleClass, doubleValueOf, &v);
      break;
    }

    case JS_TAG_NULL:
    case JS_TAG_UNDEFINED:
      result = nullptr;
      break;

    case JS_TAG_OBJECT:
      if (JS_IsArray(jsContext, value)) {
        auto arrayLengthProperty = JS_GetPropertyStr(jsContext, value, "length");
        const auto arrayLength = JS_VALUE_GET_INT(arrayLengthProperty);
        JS_FreeValue(jsContext, arrayLengthProperty);

        result = env->NewObjectArray(arrayLength, objectClass, nullptr);
        for (int i = 0; i < arrayLength && !env->ExceptionCheck(); i++) {
          auto element = JS_GetPropertyUint32(jsContext, value, i);
          auto javaElement = toJavaObject(env, element);
          if (!env->ExceptionCheck()) {
            env->SetObjectArrayElement(static_cast<jobjectArray>(result), i, javaElement);
          }
          JS_FreeValue(jsContext, element);
        }
        break;
      }
      // Fall through.
    default:
      if (throwOnUnsupportedType) {
        auto str = JS_ToCString(jsContext, value);
        throwJsExceptionFmt(env, this, "Cannot marshal value %s to Java", str);
        JS_FreeCString(jsContext, str);
      }
      result = nullptr;
      break;
  }
  return result;
}

void Context::throwJsException(JNIEnv* env, const JSValue& value) const {
  JSValue exceptionValue = JS_GetException(jsContext);

  JSValue messageValue = JS_GetPropertyStr(jsContext, exceptionValue, "message");
  JSValue stackValue = JS_GetPropertyStr(jsContext, exceptionValue, "stack");

  // If the JS does a `throw 2;`, there won't be a message property.
  jstring message = toJavaString(env,
                                 JS_IsUndefined(messageValue) ? exceptionValue : messageValue);
  JS_FreeValue(jsContext, messageValue);

  jstring stack = toJavaString(env, stackValue);
  JS_FreeValue(jsContext, stackValue);
  JS_FreeValue(jsContext, exceptionValue);

  jthrowable cause = static_cast<jthrowable>(JS_GetContextOpaque(jsContext));
  JS_SetContextOpaque(jsContext, nullptr);

  jobject exception;
  if (cause) {
    exception = env->NewLocalRef(cause);
    env->DeleteGlobalRef(cause);

    // add the JavaScript stack to this exception.
    const jmethodID addJavaScriptStack =
        env->GetStaticMethodID(quickJsExceptionClass,
                               "addJavaScriptStack",
                               "(Ljava/lang/Throwable;Ljava/lang/String;)V");
    env->CallStaticVoidMethod(quickJsExceptionClass, addJavaScriptStack, exception,
                              stack);
  } else {
    exception = env->NewObject(quickJsExceptionClass,
                               quickJsExceptionConstructor,
                               message,
                               stack);
  }

  env->DeleteLocalRef(stack);
  env->DeleteLocalRef(message);

  env->Throw(static_cast<jthrowable>(exception));
}

JSValue Context::throwJavaExceptionFromJs(JNIEnv* env) const {
  assert(env->ExceptionCheck()); // There must be something to throw.
  assert(JS_GetContextOpaque(jsContext) == nullptr); // There can't be a pending thrown exception.
  auto exception = env->ExceptionOccurred();
  env->ExceptionClear();
  JS_SetContextOpaque(jsContext, env->NewGlobalRef(exception));
  return JS_ThrowInternalError(jsContext, "Java Exception");
}

JNIEnv* Context::getEnv() const {
  JNIEnv* env = nullptr;
  javaVm->GetEnv(reinterpret_cast<void**>(&env), jniVersion);
  if (env) {
    return env;
  }

  javaVm->AttachCurrentThread(
#ifdef __ANDROID__
      &env,
#else
      reinterpret_cast<void**>(&env),
#endif
      nullptr);
  if (env) {
    thread_local JniThreadDetacher detacher(javaVm);
  }
  return env;
}

/*
 * Converts `string` to UTF-8. Prefer this over `GetStringUTFChars()` for any string that might
 * contain non-ASCII characters because that function returns modified UTF-8.
 */
std::string Context::toCppString(JNIEnv* env, jstring string) const {
  const jbyteArray utf8BytesObject = static_cast<jbyteArray>(env->CallObjectMethod(string, stringGetBytes, stringUtf8));
  size_t utf8Length = env->GetArrayLength(utf8BytesObject);
  jbyte* utf8Bytes = env->GetByteArrayElements(utf8BytesObject, NULL);
  std::string result = std::string(reinterpret_cast<char*>(utf8Bytes), utf8Length);
  env->ReleaseByteArrayElements(utf8BytesObject, utf8Bytes, JNI_ABORT);
  env->DeleteLocalRef(utf8BytesObject);
  return result;
}

JSValue Context::toJsString(JNIEnv* env, jstring javaString) const {
  std::string cppString = this->toCppString(env, javaString);
  return JS_NewString(this->jsContext, cppString.c_str());
}

/*
 * Converts `value` to a Java string. Prefer this over `NewStringUTF()` for any string that might
 * contain non-ASCII characters because that function expects modified UTF-8.
 */
jstring Context::toJavaString(JNIEnv* env, const JSValueConst& value) const {
  const char* string = JS_ToCString(jsContext, value);
  size_t utf8Length = strlen(string);
  jbyteArray utf8BytesObject = env->NewByteArray(utf8Length);
  jbyte* utf8Bytes = env->GetByteArrayElements(utf8BytesObject, NULL);
  std::copy(string, string + utf8Length, utf8Bytes);
  env->ReleaseByteArrayElements(utf8BytesObject, utf8Bytes, JNI_COMMIT);
  JS_FreeCString(jsContext, string);
  jstring result = static_cast<jstring>(env->NewObject(stringClass, stringConstructor, utf8BytesObject, stringUtf8));
  env->DeleteLocalRef(utf8BytesObject);
  return result;
}
