#include <jni.h>
#include <new>
#include <string>
#include <vector>
#include "quickjs/quickjs.h"

class JsMethodProxy {
public:
  JsMethodProxy(const char *name, jmethodID methodId)
      : name(name), methodId(methodId) {
  }

  std::string name;
  jmethodID methodId;

  JSValueConst (**argLoaders)(JNIEnv *env, JSContext *jsContext, jvalue value);
};

class JsObjectProxy {
public:
  JsObjectProxy(const char *name)
      : name(name) {
  }

  std::string name;
  std::vector<JsMethodProxy> methods;
};


static jmethodID booleanValueOf;
static jmethodID integerValueOf;
static jmethodID doubleValueOf;
static jmethodID quickJsExceptionConstructor;

class Context {
public:
  Context(JNIEnv *env)
      : env(env), jsRuntime(JS_NewRuntime()), jsContext(JS_NewContext(jsRuntime)),
        booleanClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Boolean")))),
        integerClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Integer")))),
        doubleClass(static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Double")))),
        quickJsExecptionClass(static_cast<jclass>(env->NewGlobalRef(
            env->FindClass("com/squareup/quickjs/QuickJsException")))) {
  }

  ~Context() {
    env->DeleteGlobalRef(quickJsExecptionClass);
    env->DeleteGlobalRef(doubleClass);
    env->DeleteGlobalRef(integerClass);
    env->DeleteGlobalRef(booleanClass);
    JS_FreeContext(jsContext);
    JS_FreeRuntime(jsRuntime);
  }

  JNIEnv *env;
  JSRuntime *jsRuntime;
  JSContext *jsContext;
  jclass booleanClass;
  jclass integerClass;
  jclass doubleClass;
  jclass quickJsExecptionClass;
  std::vector<JsObjectProxy> objectProxies;
};


extern "C" JNIEXPORT jlong JNICALL
Java_com_squareup_quickjs_QuickJs_createContext(JNIEnv *env, jclass type) {
  Context *c = new(std::nothrow) Context(env);
  if (!c) {
    return 0;
  }
  booleanValueOf = env->GetStaticMethodID(c->booleanClass, "valueOf",
                                          "(Z)Ljava/lang/Boolean;");

  integerValueOf = env->GetStaticMethodID(c->integerClass, "valueOf",
                                          "(I)Ljava/lang/Integer;");

  doubleValueOf = env->GetStaticMethodID(c->doubleClass, "valueOf",
                                         "(D)Ljava/lang/Double;");

  quickJsExceptionConstructor = env->GetMethodID(c->quickJsExecptionClass, "<init>",
                                                 "(Ljava/lang/String;Ljava/lang/String;)V");
  return (jlong) c;
}

extern "C" JNIEXPORT void JNICALL
Java_com_squareup_quickjs_QuickJs_destroyContext(JNIEnv *env, jobject type, jlong context_) {
  Context *context = (Context *) context_;
  delete context;
}

static void throwJavaException(JNIEnv *env, const char *exceptionClass, const char *fmt, ...) {
  char msg[512];
  va_list args;
  va_start (args, fmt);
  vsnprintf(msg, sizeof(msg), fmt, args);
  va_end (args);
  env->ThrowNew(env->FindClass(exceptionClass), msg);
}

static void throwJsExceptionFmt(JNIEnv *env, Context *context, const char *fmt, ...) {
  char msg[512];
  va_list args;
  va_start (args, fmt);
  vsnprintf(msg, sizeof(msg), fmt, args);
  va_end (args);
  jobject exception = env->NewObject(context->quickJsExecptionClass,
                                     quickJsExceptionConstructor,
                                     env->NewStringUTF(msg),
                                     NULL);
  env->Throw(static_cast<jthrowable>(exception));
}

static void throwJsException(JNIEnv *env, Context *context, JSValue value) {
  JSContext *jsContext = context->jsContext;

  JSValue exceptionValue = JS_GetException(jsContext);

  JSValue messageValue = JS_GetPropertyStr(jsContext, exceptionValue, "message");
  JSValue stackValue = JS_GetPropertyStr(jsContext, exceptionValue, "stack");

// If the JS does a `throw 2;`, there won't be a message property.
  const char *message = JS_ToCString(jsContext,
                                     JS_IsUndefined(messageValue) ? exceptionValue : messageValue);
  JS_FreeValue(jsContext, messageValue);

  const char *stack = JS_ToCString(jsContext, stackValue);
  JS_FreeValue(jsContext, stackValue);
  JS_FreeValue(jsContext, exceptionValue);

  jobject exception = env->NewObject(context->quickJsExecptionClass,
                                     quickJsExceptionConstructor,
                                     env->NewStringUTF(message),
                                     env->NewStringUTF(stack));
  JS_FreeCString(jsContext, stack);
  JS_FreeCString(jsContext, message);

  env->Throw(static_cast<jthrowable>(exception));
}

jvalue JSValueToObject(JNIEnv *env, Context *context, JSValue value) {
  jvalue result;
  switch (JS_VALUE_GET_TAG(value)) {
    case JS_TAG_EXCEPTION: {
      throwJsException(env, context, value);
      result.l = NULL;
      break;
    }

    case JS_TAG_STRING: {
      const char *string = JS_ToCString(context->jsContext, value);
      result.l = env->NewStringUTF(string);
      JS_FreeCString(context->jsContext, string);
      break;
    }

    case JS_TAG_BOOL: {
      jvalue v;
      v.z = (jboolean) JS_VALUE_GET_BOOL(value);
      result.l = env->CallStaticObjectMethodA(context->booleanClass, booleanValueOf, &v);
      break;
    }

    case JS_TAG_INT: {
      jvalue v;
      v.j = (jint) JS_VALUE_GET_INT(value);
      result.l = env->CallStaticObjectMethodA(context->integerClass, integerValueOf, &v);
      break;
    }

    case JS_TAG_FLOAT64: {
      jvalue v;
      v.d = (jdouble) JS_VALUE_GET_FLOAT64(value);
      result.l = env->CallStaticObjectMethodA(context->doubleClass, doubleValueOf, &v);
      break;
    }

    case JS_TAG_NULL:
    case JS_TAG_UNDEFINED:
    default:
      result.l = NULL;
      break;
  }
  return result;
}

extern "C" jobject JNICALL
Java_com_squareup_quickjs_QuickJs_evaluate__JLjava_lang_String_2Ljava_lang_String_2(JNIEnv *env,
                                                                                    jobject type,
                                                                                    jlong context_,
                                                                                    jstring sourceCode_,
                                                                                    jstring fileName_) {
  Context *context = reinterpret_cast<Context *>(context_);
  JSContext *jsContext = context->jsContext;

  const char *sourceCode = env->GetStringUTFChars(sourceCode_, 0);
  const char *fileName = env->GetStringUTFChars(fileName_, 0);

  JSValue evalValue = JS_Eval(jsContext, sourceCode, strlen(sourceCode), fileName, 0);

  env->ReleaseStringUTFChars(sourceCode_, sourceCode);
  env->ReleaseStringUTFChars(fileName_, fileName);

  jobject result = JSValueToObject(env, context, evalValue).l;

  JS_FreeValue(jsContext, evalValue);

  return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_squareup_quickjs_QuickJs_get(JNIEnv *env, jobject thiz, jlong _context, jstring name,
                                      jobjectArray methods) {
  Context *context = reinterpret_cast<Context *>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return 0L;
  }
  JSContext *jsContext = context->jsContext;

  JSValue global = JS_GetGlobalObject(jsContext);

  const char *nameStr = env->GetStringUTFChars(name, 0);

  JSValue obj = JS_GetPropertyStr(jsContext, global, nameStr);

  jlong result;
  if (JS_IsObject(obj)) {
    JsObjectProxy *jsObjectProxy = new JsObjectProxy(nameStr);
    jsize numMethods = env->GetArrayLength(methods);
    jmethodID getName = NULL;
    for (int i = 0; i < numMethods && !env->ExceptionCheck(); ++i) {
      jobject method = env->GetObjectArrayElement(methods, i);
      if (!getName) {
        jclass methodClass = env->GetObjectClass(method);
        getName = env->GetMethodID(methodClass, "getName", "()Ljava/lang/String;");
      }
      jstring methodName = static_cast<jstring>(env->CallObjectMethod(method, getName));
      const char *methodNameStr = env->GetStringUTFChars(methodName, 0);

      JSValue prop = JS_GetPropertyStr(jsContext, obj, methodNameStr);
      if (JS_IsFunction(jsContext, prop)) {
        jsObjectProxy->methods.emplace_back(methodNameStr, env->FromReflectedMethod(method));
      } else {
        const char *msg = JS_IsUndefined(prop)
                          ? "JavaScript global %s has no method called %s"
                          : "JavaScript property %s.%s not callable";
        throwJsExceptionFmt(env, context, msg, nameStr, methodNameStr);
      }
      JS_FreeValue(jsContext, prop);
      env->ReleaseStringUTFChars(methodName, methodNameStr);
    }
    if (!env->ExceptionCheck()) {
      result = reinterpret_cast<jlong>(jsObjectProxy);
    } else {
      delete jsObjectProxy;
      result = 0L;
    }
  } else if (JS_IsException(obj)) {
    result = 0L;
    throwJsException(env, context, obj);
  } else {
    result = 0L;
    const char *msg = JS_IsUndefined(obj)
                      ? "A global JavaScript object called %s was not found"
                      : "JavaScript global called %s is not an object";
    throwJavaException(env, "java/lang/IllegalArgumentException", msg, nameStr);
  }

  JS_FreeValue(jsContext, obj);

  env->ReleaseStringUTFChars(name, nameStr);
  JS_FreeValue(jsContext, global);

  return result;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_squareup_quickjs_QuickJs_call(JNIEnv *env, jobject thiz, jlong _context, jlong instance,
                                       jobject method, jobjectArray args) {
  Context *context = reinterpret_cast<Context *>(_context);
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return NULL;
  }
  JsObjectProxy *jsObjectProxy = reinterpret_cast<JsObjectProxy*>(instance);
  if (!jsObjectProxy) {
    throwJavaException(env, "java/lang/NullPointerException", "Invalid JavaScript object");
    return NULL;
  }

  JSValue global = JS_GetGlobalObject(context->jsContext);

  JSValue thisPointer = JS_GetPropertyStr(context->jsContext, global, jsObjectProxy->name.c_str());

  JsMethodProxy *methodProxy = NULL;
  for (int i = 0; i < jsObjectProxy->methods.size(); ++i) {
    if (env->FromReflectedMethod(method) == jsObjectProxy->methods[i].methodId) {
      methodProxy = &jsObjectProxy->methods[i];
      break;
    }
  }

  jobject result;
  if (methodProxy) {
    JSValue function = JS_GetPropertyStr(context->jsContext, thisPointer,
                                         methodProxy->name.c_str());
    JSValue callResult = JS_Call(context->jsContext, function, thisPointer, 0,
                                 NULL); // TODO: call arguments
    result = JSValueToObject(env, context, callResult).l;
    JS_FreeValue(context->jsContext, callResult);
    JS_FreeValue(context->jsContext, function);
  } else {
    const jclass methodClass = env->GetObjectClass(method);
    const jmethodID getName = env->GetMethodID(methodClass, "getName",
                                               "()Ljava/lang/String;");
    jstring methodName = static_cast<jstring>(env->CallObjectMethod(method, getName));
    const char *methodNameStr = env->GetStringUTFChars(methodName, 0);
    throwJsExceptionFmt(env, context, "Could not find method %s.%s", jsObjectProxy->name.c_str(),
                        methodNameStr);
    env->ReleaseStringUTFChars(methodName, methodNameStr);
    result = NULL;
  }
  JS_FreeValue(context->jsContext, thisPointer);
  JS_FreeValue(context->jsContext, global);
  return result;
}
