#include <string.h>
#include <jni.h>
#include "duktape.h"

static JavaVM *jvm = NULL;
static jclass duktape = NULL;
static jmethodID getLocalTimeZoneOffset = NULL;

JNIEXPORT jlong JNICALL
Java_com_squareup_duktape_Duktape_createContext(JNIEnv *env, jclass type) {
  if (jvm == NULL) {
    (*env)->GetJavaVM(env, &jvm);
    duktape = (*env)->NewGlobalRef(env, type);
    getLocalTimeZoneOffset = (*env)->GetStaticMethodID(env, duktape, "getLocalTimeZoneOffset", "(D)I");
  }
  return (jlong) duk_create_heap_default();
}

JNIEXPORT void JNICALL
Java_com_squareup_duktape_Duktape_destroyContext(JNIEnv *env, jclass type, jlong context) {
  duk_destroy_heap((void*) context);
}

static duk_int_t eval_string_with_filename(void* ctx, const char* src, const char* fileName) {
  duk_push_string(ctx, fileName);
  return duk_eval_raw(ctx, src, 0, DUK_COMPILE_EVAL | DUK_COMPILE_SAFE |
                                   DUK_COMPILE_NOSOURCE | DUK_COMPILE_STRLEN);
}

JNIEXPORT jstring JNICALL
Java_com_squareup_duktape_Duktape_evaluate__JLjava_lang_String_2Ljava_lang_String_2(JNIEnv *env,
                                                                                    jclass type,
                                                                                    jlong context,
                                                                                    jstring sourceCode_,
                                                                                    jstring fileName_) {
  const char* sourceCode = (*env)->GetStringUTFChars(env, sourceCode_, 0);
  const char* fileName = (*env)->GetStringUTFChars(env, fileName_, 0);

  void* ctx = (void*) context;
  jstring result = NULL;

  if (eval_string_with_filename(ctx, sourceCode, fileName) != 0) {
    jclass Exception = (*env)->FindClass(env, "com/squareup/duktape/DuktapeException");

    // If it's a duktape error object, try to pull out the full stacktrace.
    if (duk_is_error(ctx, -1) && duk_get_prop_string(ctx, -1, "stack")) {
      (*env)->ThrowNew(env, Exception, duk_safe_to_string(ctx, -1));
      duk_pop(ctx);
    } else {
      // Not an error or no stacktrace, just convert to a string.
      (*env)->ThrowNew(env, Exception, duk_safe_to_string(ctx, -1));
    }
  } else {
    result = (*env)->NewStringUTF(env, duk_get_string(ctx, -1));
  }

  duk_pop(ctx);

  (*env)->ReleaseStringUTFChars(env, sourceCode_, sourceCode);
  (*env)->ReleaseStringUTFChars(env, fileName_, fileName);

  return result;
}

duk_int_t android__get_local_tzoffset(duk_double_t time) {
  JNIEnv* env;
  (*jvm)->AttachCurrentThread(jvm, &env, NULL);
  return (*env)->CallStaticIntMethod(env, duktape, getLocalTimeZoneOffset, time);
}
