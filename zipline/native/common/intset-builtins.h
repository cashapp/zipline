#ifndef QUICKJS_ANDROID_INTSETBUILTINS_H
#define QUICKJS_ANDROID_INTSETBUILTINS_H

#include "../quickjs/quickjs.h"

#ifdef __cplusplus
extern "C" {
#endif

void js_intset_register_builtins(JSContext *jsContext);

#ifdef __cplusplus
} /* extern "C" { */
#endif

#endif //QUICKJS_ANDROID_INTSETBUILTINS_H
