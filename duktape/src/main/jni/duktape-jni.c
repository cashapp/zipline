#include <string.h>
#include <jni.h>
#include "duktape.h"

JNIEXPORT jlong JNICALL
Java_com_squareup_duktape_Duktape_createContext(JNIEnv *env, jclass type) {
  return (jlong) duk_create_heap_default();
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_Duktape_destroyContext(JNIEnv *env, jclass type, jlong context) {
  duk_destroy_heap((void*) context);
}

JNIEXPORT jstring JNICALL
Java_com_squareup_duktape_Duktape_evaluate__JLjava_lang_String_2(JNIEnv *env,
                                                                 jclass type,
                                                                 jlong context,
                                                                 jstring s_) {
  void* ctx = (void*) context;
  const char *s = (*env)->GetStringUTFChars(env, s_, 0);

  jstring result = NULL;

  if (duk_peval_string(ctx, s) != 0) {
    jclass Exception = (*env)->FindClass(env, "com/squareup/duktape/DuktapeException");
    (*env)->ThrowNew(env, Exception, duk_safe_to_string(ctx, -1));
  } else {
    result = (*env)->NewStringUTF(env, duk_get_string(ctx, -1));
  }

  duk_pop(ctx);
  (*env)->ReleaseStringUTFChars(env, s_, s);
  return result;
}