#include <string.h>
#include <jni.h>
#include <malloc.h>
#include "quickjs/quickjs.h"

typedef struct {
  JSRuntime* jsRuntime;
  JSContext* jsContext;
  jclass booleanClass;
  jclass integerClass;
  jclass doubleClass;
  jclass quickJsExecptionClass;
} Context;

static jmethodID booleanValueOf;
static jmethodID integerValueOf;
static jmethodID doubleValueOf;
static jmethodID quickJsExceptionConstructor;

JNIEXPORT jlong JNICALL
Java_com_squareup_quickjs_QuickJs_createContext(JNIEnv* env, jclass type) {
  Context* c = calloc(1, sizeof(Context));
  if (!c) {
    return 0;
  }
  c->jsRuntime = JS_NewRuntime();
  if (!c->jsRuntime) {
    goto cleanup;
  }
  c->jsContext = JS_NewContext(c->jsRuntime);
  if (!c->jsContext) {
    goto cleanup;
  }

  c->booleanClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/Boolean"));
  booleanValueOf = (*env)->GetStaticMethodID(env, c->booleanClass, "valueOf",
                                             "(Z)Ljava/lang/Boolean;");

  c->integerClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/Integer"));
  integerValueOf = (*env)->GetStaticMethodID(env, c->integerClass, "valueOf",
                                             "(I)Ljava/lang/Integer;");

  c->doubleClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/Double"));
  doubleValueOf = (*env)->GetStaticMethodID(env, c->doubleClass, "valueOf",
                                            "(D)Ljava/lang/Double;");

  c->quickJsExecptionClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env,
                                                  "com/squareup/quickjs/QuickJsException"));
  quickJsExceptionConstructor = (*env)->GetMethodID(env, c->quickJsExecptionClass, "<init>",
                                                    "(Ljava/lang/String;Ljava/lang/String;)V");
  return (jlong) c;

  cleanup:
  if (c->jsContext) {
    JS_FreeContext(c->jsContext);
  }
  if (c->jsRuntime) {
    JS_FreeRuntime(c->jsRuntime);
  }
  free(c);
  return 0;
}

JNIEXPORT void JNICALL
Java_com_squareup_quickjs_QuickJs_destroyContext(JNIEnv* env, jobject type, jlong context_) {
  Context* context = (Context*) context_;
  (*env)->DeleteGlobalRef(env, context->quickJsExecptionClass);
  (*env)->DeleteGlobalRef(env, context->doubleClass);
  (*env)->DeleteGlobalRef(env, context->integerClass);
  (*env)->DeleteGlobalRef(env, context->booleanClass);
  JS_FreeContext(context->jsContext);
  JS_FreeRuntime(context->jsRuntime);
  free(context);
}

static void throwJavaException(JNIEnv* env, const char* exceptionClass, const char* fmt, ...) {
  char msg[512];
  va_list args;
  va_start (args, fmt);
  vsnprintf(msg, sizeof(msg), fmt, args);
  va_end (args);
  (*env)->ThrowNew(env, (*env)->FindClass(env, exceptionClass), msg);
}

static void throwJsExceptionFmt(JNIEnv* env, Context* context, const char* fmt, ...) {
  char msg[512];
  va_list args;
  va_start (args, fmt);
  vsnprintf(msg, sizeof(msg), fmt, args);
  va_end (args);
  jobject exception = (*env)->NewObject(env, context->quickJsExecptionClass,
                                        quickJsExceptionConstructor,
                                        (*env)->NewStringUTF(env, msg),
                                        NULL);
  (*env)->Throw(env, exception);
}

static void throwJsException(JNIEnv* env, Context* context, JSValue value) {
  JSContext* jsContext = context->jsContext;

  JSValue exceptionValue = JS_GetException(jsContext);

  JSValue messageValue = JS_GetPropertyStr(jsContext, exceptionValue, "message");
  JSValue stackValue = JS_GetPropertyStr(jsContext, exceptionValue, "stack");

  // If the JS does a `throw 2;`, there won't be a message property.
  const char* message = JS_ToCString(jsContext,
                                     JS_IsUndefined(messageValue) ? exceptionValue : messageValue);
  JS_FreeValue(jsContext, messageValue);

  const char* stack = JS_ToCString(jsContext, stackValue);
  JS_FreeValue(jsContext, stackValue);
  JS_FreeValue(jsContext, exceptionValue);

  jobject exception = (*env)->NewObject(env, context->quickJsExecptionClass,
                                        quickJsExceptionConstructor,
                                        (*env)->NewStringUTF(env, message),
                                        (*env)->NewStringUTF(env, stack));
  JS_FreeCString(jsContext, stack);
  JS_FreeCString(jsContext, message);

  (*env)->Throw(env, exception);
}

jvalue JSValueToObject(JNIEnv* env, Context* context, JSValue value) {
  jvalue result;
  switch (JS_VALUE_GET_TAG(value)) {
    case JS_TAG_EXCEPTION: {
      throwJsException(env, context, value);
      result.l = NULL;
      break;
    }

    case JS_TAG_STRING: {
      const char* string = JS_ToCString(context->jsContext, value);
      result.l = (*env)->NewStringUTF(env, string);
      JS_FreeCString(context->jsContext, string);
      break;
    }

    case JS_TAG_BOOL: {
      jvalue v;
      v.z = (jboolean) JS_VALUE_GET_BOOL(value);
      result.l = (*env)->CallStaticObjectMethodA(env, context->booleanClass, booleanValueOf, &v);
      break;
    }

    case JS_TAG_INT: {
      jvalue v;
      v.j = (jint) JS_VALUE_GET_INT(value);
      result.l = (*env)->CallStaticObjectMethodA(env, context->integerClass, integerValueOf, &v);
      break;
    }

    case JS_TAG_FLOAT64: {
      jvalue v;
      v.d = (jdouble) JS_VALUE_GET_FLOAT64(value);
      result.l = (*env)->CallStaticObjectMethodA(env, context->doubleClass, doubleValueOf, &v);
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

jobject JNICALL
Java_com_squareup_quickjs_QuickJs_evaluate__JLjava_lang_String_2Ljava_lang_String_2(JNIEnv* env,
                                                                                    jobject type,
                                                                                    jlong context_,
                                                                                    jstring sourceCode_,
                                                                                    jstring fileName_) {
  Context* context = (Context*) context_;
  JSContext* jsContext = context->jsContext;

  const char* sourceCode = (*env)->GetStringUTFChars(env, sourceCode_, 0);
  const char* fileName = (*env)->GetStringUTFChars(env, fileName_, 0);

  JSValue evalValue = JS_Eval(jsContext, sourceCode, strlen(sourceCode), fileName, 0);

  (*env)->ReleaseStringUTFChars(env, sourceCode_, sourceCode);
  (*env)->ReleaseStringUTFChars(env, fileName_, fileName);

  jobject result = JSValueToObject(env, context, evalValue).l;

  JS_FreeValue(jsContext, evalValue);

  return result;
}

typedef struct {
  char* name;
  jmethodID methodId;

  JSValueConst (** argLoaders)(JNIEnv* env, JSContext* jsContext, jvalue value);
} JsMethodProxy;

typedef struct {
  char* name;
  int numMethods;
  JsMethodProxy* methods;
} JsObjectProxy;

static void deleteJsObjectProxy(JsObjectProxy* jsObjectProxy) {
  if (!jsObjectProxy) {
    return;
  }
  if (jsObjectProxy->name) {
    free(jsObjectProxy->name);
  }
  for (int i = 0; jsObjectProxy->methods && i < jsObjectProxy->numMethods; ++i) {
    if (jsObjectProxy->methods[i].argLoaders) {
      free(jsObjectProxy->methods[i].argLoaders);
    }
    if (jsObjectProxy->methods[i].name) {
      free(jsObjectProxy->methods[i].name);
    }
    free(jsObjectProxy->methods);
  }
  free(jsObjectProxy);
}

JNIEXPORT jlong JNICALL
Java_com_squareup_quickjs_QuickJs_get(JNIEnv* env, jobject thiz, jlong _context, jstring name,
                                      jobjectArray methods) {
  Context* context = (Context*) _context;
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return 0L;
  }
  JSContext* jsContext = context->jsContext;

  JSValue global = JS_GetGlobalObject(jsContext);

  const char* nameStr = (*env)->GetStringUTFChars(env, name, 0);

  JSValue obj = JS_GetPropertyStr(jsContext, global, nameStr);

  jlong result;
  if (JS_IsObject(obj)) {
    JsObjectProxy* jsObjectProxy = malloc(sizeof(JsObjectProxy));
    jsObjectProxy->name = strdup(nameStr);
    jsize numMethods = (*env)->GetArrayLength(env, methods);
    jsObjectProxy->numMethods = numMethods;
    jsObjectProxy->methods = calloc(numMethods, sizeof(JsMethodProxy));
    jmethodID getName = NULL;
    for (int i = 0; i < numMethods && !(*env)->ExceptionCheck(env); ++i) {
      jobject method = (*env)->GetObjectArrayElement(env, methods, i);
      if (!getName) {
        jclass methodClass = (*env)->GetObjectClass(env, method);
        getName = (*env)->GetMethodID(env, methodClass, "getName", "()Ljava/lang/String;");
      }
      jstring methodName = (*env)->CallObjectMethod(env, method, getName);
      const char* methodNameStr = (*env)->GetStringUTFChars(env, methodName, 0);

      JSValue prop = JS_GetPropertyStr(jsContext, obj, methodNameStr);
      if (JS_IsFunction(jsContext, prop)) {
        jsObjectProxy->methods[i].name = strdup(methodNameStr);
        jsObjectProxy->methods[i].methodId = (*env)->FromReflectedMethod(env, method);
      } else {
        const char* msg = JS_IsUndefined(prop)
                          ? "JavaScript global %s has no method called %s"
                          : "JavaScript property %s.%s not callable";
        throwJsExceptionFmt(env, context, msg, nameStr, methodNameStr);
      }
      JS_FreeValue(jsContext, prop);
      (*env)->ReleaseStringUTFChars(env, methodName, methodNameStr);
    }
    if (!(*env)->ExceptionCheck(env)) {
      result = (jlong) jsObjectProxy;
      // TODO: hook up the JS side's finalizer to call deleteJsObjectProxy().
    } else {
      deleteJsObjectProxy(jsObjectProxy);
      result = 0L;
    }
  } else if (JS_IsException(obj)) {
    result = 0L;
    throwJsException(env, context, obj);
  } else {
    result = 0L;
    const char* msg = JS_IsUndefined(obj)
                      ? "A global JavaScript object called %s was not found"
                      : "JavaScript global called %s is not an object";
    throwJavaException(env, "java/lang/IllegalArgumentException", msg, nameStr);
  }

  JS_FreeValue(jsContext, obj);

  (*env)->ReleaseStringUTFChars(env, name, nameStr);
  JS_FreeValue(jsContext, global);

  return result;
}

JNIEXPORT jobject JNICALL
Java_com_squareup_quickjs_QuickJs_call(JNIEnv* env, jobject thiz, jlong _context, jlong instance,
                                       jobject method, jobjectArray args) {
  Context* context = (Context*) _context;
  if (!context) {
    throwJavaException(env, "java/lang/NullPointerException",
                       "Null QuickJs context - did you close your QuickJs?");
    return NULL;
  }
  JsObjectProxy* jsObjectProxy = (JsObjectProxy*) instance;
  if (!jsObjectProxy) {
    throwJavaException(env, "java/lang/NullPointerException", "Invalid JavaScript object");
    return NULL;
  }

  JSValue global = JS_GetGlobalObject(context->jsContext);

  JSValue this = JS_GetPropertyStr(context->jsContext, global, jsObjectProxy->name);

  JsMethodProxy* methodProxy = NULL;
  for (int i = 0; i < jsObjectProxy->numMethods; ++i) {
    if ((*env)->FromReflectedMethod(env, method) == jsObjectProxy->methods[i].methodId) {
      methodProxy = &jsObjectProxy->methods[i];
      break;
    }
  }

  jobject result;
  if (methodProxy) {
    JSValue function = JS_GetPropertyStr(context->jsContext, this, methodProxy->name);
    JSValue callResult = JS_Call(context->jsContext, function, this, 0,
                                 NULL); // TODO: call arguments
    result = JSValueToObject(env, context, callResult).l;
    JS_FreeValue(context->jsContext, callResult);
    JS_FreeValue(context->jsContext, function);
  } else {
    const jclass methodClass = (*env)->GetObjectClass(env, method);
    const jmethodID getName = (*env)->GetMethodID(env, methodClass, "getName",
                                                  "()Ljava/lang/String;");
    jstring methodName = (*env)->CallObjectMethod(env, method, getName);
    const char* methodNameStr = (*env)->GetStringUTFChars(env, methodName, 0);
    throwJsExceptionFmt(env, context, "Could not find method %s.%s", jsObjectProxy->name,
                        methodNameStr);
    (*env)->ReleaseStringUTFChars(env, methodName, methodNameStr);
  }
  JS_FreeValue(context->jsContext, this);
  JS_FreeValue(context->jsContext, global);
  return result;
}
