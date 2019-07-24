#include <string.h>
#include <jni.h>
#include <malloc.h>
#include "quickjs/quickjs.h"

JNIEXPORT jlong JNICALL
Java_com_squareup_quickjs_QuickJs_createContext(JNIEnv *env, jclass type) {
  JSRuntime* runtime = JS_NewRuntime();
  if (!runtime) {
    return (jlong) 0; // Trigger OOM from Java-side.
  }
  JSContext* context = JS_NewContext(runtime);
  return (jlong) context;
}

JNIEXPORT void JNICALL
Java_com_squareup_quickjs_QuickJs_destroyContext(JNIEnv *env, jclass type, jlong context_) {
  JSContext* context = (JSContext*) context_;
  JSRuntime* runtime = JS_GetRuntime(context);
  JS_FreeContext(context);
  JS_FreeRuntime(runtime);
}

JNIEXPORT jstring JNICALL
Java_com_squareup_quickjs_QuickJs_evaluate__JLjava_lang_String_2Ljava_lang_String_2(JNIEnv *env,
                                                                                    jclass type,
                                                                                    jlong context_,
                                                                                    jstring sourceCode_,
                                                                                    jstring fileName_) {
  JSContext* context = (JSContext*) context_;

  const char *sourceCode = (*env)->GetStringUTFChars(env, sourceCode_, 0);
  const char *fileName = (*env)->GetStringUTFChars(env, fileName_, 0);

  JSValue evalValue = JS_Eval(context, sourceCode, strlen(sourceCode), fileName, 0);

  jstring result = NULL;
  if (JS_IsException(evalValue)) {
    JS_FreeValue(context, evalValue);

    JSValue exceptionValue = JS_GetException(context);

    JSValue messageValue = JS_GetPropertyStr(context, exceptionValue, "message");
    JS_FreeValue(context, exceptionValue);

    const char *message = JS_ToCString(context, messageValue);
    JS_FreeValue(context, messageValue);

    const char *ownedMessage = strdup(message);
    JS_FreeCString(context, message);

    jclass Exception = (*env)->FindClass(env, "com/squareup/quickjs/QuickJsException");
    (*env)->ThrowNew(env, Exception, ownedMessage);
  } else {
    JSValue stringValue = JS_ToString(context, evalValue);
    JS_FreeValue(context, evalValue);

    const char *string = JS_ToCString(context, stringValue);
    JS_FreeValue(context, stringValue);

    const char *ownedString = strdup(string);
    JS_FreeCString(context, string);

    result = (*env)->NewStringUTF(env, ownedString);
  }

  (*env)->ReleaseStringUTFChars(env, sourceCode_, sourceCode);
  (*env)->ReleaseStringUTFChars(env, fileName_, fileName);
  return result;
}
