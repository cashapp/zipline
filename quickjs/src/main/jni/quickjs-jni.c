#include <string.h>
#include <jni.h>
#include <malloc.h>
#include "quickjs/quickjs.h"

// TODO(szurbrigg): delete these global refs on teardown.
static jclass booleanClass;
static jmethodID booleanValueOf;
static jclass integerClass;
static jmethodID integerValueOf;
static jclass doubleClass;
static jmethodID doubleValueOf;

JNIEXPORT jlong JNICALL
Java_com_squareup_quickjs_QuickJs_createContext(JNIEnv *env, jclass type) {
  booleanClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/Boolean"));
  booleanValueOf = (*env)->GetStaticMethodID(env, booleanClass, "valueOf",
                                             "(Z)Ljava/lang/Boolean;");

  integerClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/Integer"));
  integerValueOf = (*env)->GetStaticMethodID(env, integerClass, "valueOf",
                                             "(I)Ljava/lang/Integer;");

  doubleClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/Double"));
  doubleValueOf = (*env)->GetStaticMethodID(env, doubleClass, "valueOf",
                                            "(D)Ljava/lang/Double;");

  JSRuntime *runtime = JS_NewRuntime();
  if (!runtime) {
    return (jlong) 0; // Trigger OOM from Java-side.
  }
  JSContext *context = JS_NewContext(runtime);
  return (jlong) context;
}

JNIEXPORT void JNICALL
Java_com_squareup_quickjs_QuickJs_destroyContext(JNIEnv *env, jclass type, jlong context_) {
  JSContext *context = (JSContext *) context_;
  JSRuntime *runtime = JS_GetRuntime(context);
  JS_FreeContext(context);
  JS_FreeRuntime(runtime);
}

JNIEXPORT jobject JNICALL
Java_com_squareup_quickjs_QuickJs_evaluate__JLjava_lang_String_2Ljava_lang_String_2(JNIEnv *env,
                                                                                    jclass type,
                                                                                    jlong context_,
                                                                                    jstring sourceCode_,
                                                                                    jstring fileName_) {
  JSContext *context = (JSContext *) context_;

  const char *sourceCode = (*env)->GetStringUTFChars(env, sourceCode_, 0);
  const char *fileName = (*env)->GetStringUTFChars(env, fileName_, 0);

  JSValue evalValue = JS_Eval(context, sourceCode, strlen(sourceCode), fileName, 0);

  (*env)->ReleaseStringUTFChars(env, sourceCode_, sourceCode);
  (*env)->ReleaseStringUTFChars(env, fileName_, fileName);

  jstring result;
  switch (JS_VALUE_GET_TAG(evalValue)) {
    case JS_TAG_EXCEPTION: {
      JSValue exceptionValue = JS_GetException(context);

      JSValue messageValue = JS_GetPropertyStr(context, exceptionValue, "message");
      JSValue stackValue = JS_GetPropertyStr(context, exceptionValue, "stack");
      JS_FreeValue(context, exceptionValue);

      const char *message = JS_ToCString(context, messageValue);
      JS_FreeValue(context, messageValue);

      const char *stack = JS_ToCString(context, stackValue);
      JS_FreeValue(context, stackValue);

      jclass Exception = (*env)->FindClass(env, "com/squareup/quickjs/QuickJsException");
      char *messageAndStack = (char*) malloc(strlen(message) + strlen(stack) + 2);
      strcpy(messageAndStack, message);
      strcat(messageAndStack, "\n");
      strcat(messageAndStack, stack);
      JS_FreeCString(context, stack);
      JS_FreeCString(context, message);

      (*env)->ThrowNew(env, Exception, messageAndStack);
      free(messageAndStack);
      result = NULL;
      break;
    }

    case JS_TAG_STRING: {
      const char *string = JS_ToCString(context, evalValue);
      result = (*env)->NewStringUTF(env, string);
      JS_FreeCString(context, string);
      break;
    }

    case JS_TAG_BOOL: {
      jvalue v;
      v.z = (jboolean) JS_VALUE_GET_BOOL(evalValue);
      result = (*env)->CallStaticObjectMethodA(env, booleanClass, booleanValueOf, &v);
      break;
    }

    case JS_TAG_INT: {
      jvalue v;
      v.j = (jint) JS_VALUE_GET_INT(evalValue);
      result = (*env)->CallStaticObjectMethodA(env, integerClass, integerValueOf, &v);
      break;
    }

    case JS_TAG_FLOAT64: {
      jvalue v;
      v.d = (jdouble) JS_VALUE_GET_FLOAT64(evalValue);
      result = (*env)->CallStaticObjectMethodA(env, doubleClass, doubleValueOf, &v);
      break;
    }

    case JS_TAG_NULL:
    case JS_TAG_UNDEFINED:
    default:
      result = NULL;
      break;
  }

  JS_FreeValue(context, evalValue);

  return result;
}
