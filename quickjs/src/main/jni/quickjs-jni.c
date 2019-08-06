#include <string.h>
#include <jni.h>
#include <malloc.h>
#include "quickjs/quickjs.h"

typedef struct {
  JSRuntime *jsRuntime;
  JSContext *jsContext;
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
Java_com_squareup_quickjs_QuickJs_createContext(JNIEnv *env, jclass type) {
  Context *c = malloc(sizeof(Context));
  if (!c) {
    return 0;
  }
  memset(c, 0, sizeof(Context));
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
Java_com_squareup_quickjs_QuickJs_destroyContext(JNIEnv *env, jclass type, jlong context_) {
  Context *context = (Context *) context_;
  (*env)->DeleteGlobalRef(env, context->quickJsExecptionClass);
  (*env)->DeleteGlobalRef(env, context->doubleClass);
  (*env)->DeleteGlobalRef(env, context->integerClass);
  (*env)->DeleteGlobalRef(env, context->booleanClass);
  JS_FreeContext(context->jsContext);
  JS_FreeRuntime(context->jsRuntime);
  free(context);
}

JNIEXPORT jobject JNICALL
Java_com_squareup_quickjs_QuickJs_evaluate__JLjava_lang_String_2Ljava_lang_String_2(JNIEnv *env,
                                                                                    jclass type,
                                                                                    jlong context_,
                                                                                    jstring sourceCode_,
                                                                                    jstring fileName_) {
  Context *context = (Context *) context_;
  JSContext *jsContext = context->jsContext;

  const char *sourceCode = (*env)->GetStringUTFChars(env, sourceCode_, 0);
  const char *fileName = (*env)->GetStringUTFChars(env, fileName_, 0);

  JSValue evalValue = JS_Eval(jsContext, sourceCode, strlen(sourceCode), fileName, 0);

  (*env)->ReleaseStringUTFChars(env, sourceCode_, sourceCode);
  (*env)->ReleaseStringUTFChars(env, fileName_, fileName);

  jstring result;
  switch (JS_VALUE_GET_TAG(evalValue)) {
    case JS_TAG_EXCEPTION: {
      JSValue exceptionValue = JS_GetException(jsContext);

      JSValue messageValue = JS_GetPropertyStr(jsContext, exceptionValue, "message");
      JSValue stackValue = JS_GetPropertyStr(jsContext, exceptionValue, "stack");
      JS_FreeValue(jsContext, exceptionValue);

      const char *message = JS_ToCString(jsContext, messageValue);
      JS_FreeValue(jsContext, messageValue);

      const char *stack = JS_ToCString(jsContext, stackValue);
      JS_FreeValue(jsContext, stackValue);


      jobject exception = (*env)->NewObject(env, context->quickJsExecptionClass,
                                            quickJsExceptionConstructor,
                                            (*env)->NewStringUTF(env, message),
                                            (*env)->NewStringUTF(env, stack));
      JS_FreeCString(jsContext, stack);
      JS_FreeCString(jsContext, message);

      (*env)->Throw(env, exception);
      result = NULL;
      break;
    }

    case JS_TAG_STRING: {
      const char *string = JS_ToCString(jsContext, evalValue);
      result = (*env)->NewStringUTF(env, string);
      JS_FreeCString(jsContext, string);
      break;
    }

    case JS_TAG_BOOL: {
      jvalue v;
      v.z = (jboolean) JS_VALUE_GET_BOOL(evalValue);
      result = (*env)->CallStaticObjectMethodA(env, context->booleanClass, booleanValueOf, &v);
      break;
    }

    case JS_TAG_INT: {
      jvalue v;
      v.j = (jint) JS_VALUE_GET_INT(evalValue);
      result = (*env)->CallStaticObjectMethodA(env, context->integerClass, integerValueOf, &v);
      break;
    }

    case JS_TAG_FLOAT64: {
      jvalue v;
      v.d = (jdouble) JS_VALUE_GET_FLOAT64(evalValue);
      result = (*env)->CallStaticObjectMethodA(env, context->doubleClass, doubleValueOf, &v);
      break;
    }

    case JS_TAG_NULL:
    case JS_TAG_UNDEFINED:
    default:
      result = NULL;
      break;
  }

  JS_FreeValue(jsContext, evalValue);

  return result;
}
