/*
 * Copyright (C) 2024 Square, Inc.
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
 *
 * C builtins for the kotlinx.collections.IntSet / kotlin.Long arithmetic that dominates
 * the Compose recomposition hot path in JS. The generated Kotlin/JS stdlib defines `Long`
 * as a class with two numeric fields (v4_1 = low 32 bits, w4_1 = high 32 bits). Every
 * IntSet operation (multiply, add, subtract, bitwiseAnd, etc.) does Long arithmetic through
 * the stdlib's module-local top-level functions. We register C builtins for those
 * functions on the JS global, then a build-time post-processor rewrites the stdlib
 * function bodies to call these builtins. The arithmetic becomes a single C call per
 * operation (~20-50ns vs ~5-10us in pure JS).
 */
#include "quickjs/quickjs.h"
#include <stdint.h>

// Unpack either a Kotlin/JS Long instance (object with v4_1=low, w4_1=high) or a plain
// JS number into a native int64_t. Kotlin/JS's Long arithmetic is sometimes called with
// raw numbers instead of proper Long objects (e.g. `shiftLeft(normalMillis, 1)` where
// `normalMillis` is a JS number, or `add(this, fromInt(1))` where `fromInt(1)` is a
// number passed as the operand). For a number we treat it as a 32-bit signed int and
// sign-extend; for an object we read v4_1/w4_1.
//
// Important: hi/lo are stored as JS Int32 with values that should be interpreted as
// unsigned 32-bit halves of a signed 64-bit Long. Sign-extending hi to int64_t before
// shifting is wrong (and would shift the sign bits into UB territory). Instead, zero-
// extend hi to uint64_t first, shift by 32, OR in the unsigned low half.
static inline int64_t long_unpack(JSContext *ctx, JSValueConst val) {
  int tag = JS_VALUE_GET_TAG(val);
  if (tag == JS_TAG_INT) {
    int32_t i = JS_VALUE_GET_INT(val);
    return (int64_t)i;
  }
  JSValue loVal = JS_GetPropertyStr(ctx, val, "v4_1");
  JSValue hiVal = JS_GetPropertyStr(ctx, val, "w4_1");
  int32_t lo = JS_VALUE_GET_INT(loVal);
  int32_t hi = JS_VALUE_GET_INT(hiVal);
  JS_FreeValue(ctx, loVal);
  JS_FreeValue(ctx, hiVal);
  uint64_t uhi = (uint32_t)hi;
  uint64_t ulo = (uint32_t)lo;
  return (int64_t)((uhi << 32) | ulo);
}

// valueOf: converts the (v4_1, w4_1) halves to a JS double, matching
// kotlin.Long.toNumber(). Used so JS expressions like `id - someLong` produce a
// number instead of NaN.
static JSValue long_valueOf_impl(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)argv;
  JSValue loV = JS_GetPropertyStr(ctx, this_val, "v4_1");
  JSValue hiV = JS_GetPropertyStr(ctx, this_val, "w4_1");
  int32_t lo = JS_VALUE_GET_INT(loV);
  int32_t hi = JS_VALUE_GET_INT(hiV);
  JS_FreeValue(ctx, loV);
  JS_FreeValue(ctx, hiV);
  double d = (double)(int64_t)(((uint64_t)(uint32_t)hi << 32) | (uint32_t)lo);
  return JS_NewFloat64(ctx, d);
}

// Cached kotlin.Long.prototype (set lazily on first use). The stdlib module exposes it
// on globalThis as `kotlinLongPrototype` via a small hook injected by patch-stdlib.py.
static JSValue g_long_proto = {0};
static int g_long_proto_set = 0;

static JSValue find_kotlin_long_proto(JSContext *ctx) {
  if (g_long_proto_set) return g_long_proto;
  JSValue global = JS_GetGlobalObject(ctx);
  JSValue proto = JS_GetPropertyStr(ctx, global, "kotlinLongPrototype");
  JS_FreeValue(ctx, global);
  g_long_proto = proto;
  g_long_proto_set = 1;
  return g_long_proto;
}

// Pack a native int64_t into a JS object with the kotlin.Long prototype so `instanceof Long`
// returns true (THROW_CCE() and friends keep working) and all of Long's instance methods
// (valueOf, toString, equals, hashCode, compareTo) are inherited. The two numeric halves
// are stored as own properties v4_1/w4_1 so the stdlib's arithmetic functions can still
// read them directly when needed. valueOf is overridden so `id - someLong` produces a
// number rather than NaN.
static inline JSValue long_pack(JSContext *ctx, int64_t v) {
  int32_t lo = (int32_t)(v & 0xFFFFFFFF);
  int32_t hi = (int32_t)((v >> 32) & 0xFFFFFFFF);
  JSValue proto = find_kotlin_long_proto(ctx);
  JSValue obj;
  if (JS_VALUE_GET_TAG(proto) == JS_TAG_OBJECT) {
    obj = JS_NewObjectProto(ctx, proto);
  } else {
    obj = JS_NewObject(ctx);
  }
  JS_DefinePropertyValueStr(ctx, obj, "v4_1", JS_NewInt32(ctx, lo), JS_PROP_C_W_E);
  JS_DefinePropertyValueStr(ctx, obj, "w4_1", JS_NewInt32(ctx, hi), JS_PROP_C_W_E);
  JSValue toNumSrc = JS_NewCFunction(ctx, long_valueOf_impl, "valueOf", 0);
  JS_DefinePropertyValueStr(ctx, obj, "valueOf", toNumSrc, JS_PROP_C_W_E);
  return obj;
}

static JSValue c_long_add(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return long_pack(ctx, long_unpack(ctx, argv[0]) + long_unpack(ctx, argv[1]));
}

static JSValue c_long_sub(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return long_pack(ctx, long_unpack(ctx, argv[0]) - long_unpack(ctx, argv[1]));
}

static JSValue c_long_mul(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return long_pack(ctx, long_unpack(ctx, argv[0]) * long_unpack(ctx, argv[1]));
}

static JSValue c_long_neg(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return long_pack(ctx, -long_unpack(ctx, argv[0]));
}

static JSValue c_long_and(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return long_pack(ctx, long_unpack(ctx, argv[0]) & long_unpack(ctx, argv[1]));
}

static JSValue c_long_or(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return long_pack(ctx, long_unpack(ctx, argv[0]) | long_unpack(ctx, argv[1]));
}

static JSValue c_long_xor(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return long_pack(ctx, long_unpack(ctx, argv[0]) ^ long_unpack(ctx, argv[1]));
}

static JSValue c_long_shl(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  int64_t a = long_unpack(ctx, argv[0]);
  int32_t n = JS_VALUE_GET_INT(argv[1]) & 0x3F;
  return long_pack(ctx, (int64_t)((uint64_t)a << n));
}

static JSValue c_long_shr(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  int64_t a = long_unpack(ctx, argv[0]);
  int32_t n = JS_VALUE_GET_INT(argv[1]) & 0x3F;
  return long_pack(ctx, a >> n);
}

static JSValue c_long_ushr(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  int64_t a = long_unpack(ctx, argv[0]);
  int32_t n = JS_VALUE_GET_INT(argv[1]) & 0x3F;
  return long_pack(ctx, (int64_t)((uint64_t)a >> n));
}

static JSValue c_long_fromInt(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)this_val; (void)argc;
  return long_pack(ctx, JS_VALUE_GET_INT(argv[0]));
}

static JSValue c_long_invert(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return long_pack(ctx, ~long_unpack(ctx, argv[0]));
}

static JSValue c_long_equals(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return JS_NewBool(ctx, long_unpack(ctx, argv[0]) == long_unpack(ctx, argv[1]));
}

static JSValue c_long_less(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return JS_NewBool(ctx, long_unpack(ctx, argv[0]) < long_unpack(ctx, argv[1]));
}

static JSValue c_long_compare(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  int64_t a = long_unpack(ctx, argv[0]);
  int64_t b = long_unpack(ctx, argv[1]);
  return JS_NewInt32(ctx, (a < b) ? -1 : (a > b) ? 1 : 0);
}

static JSValue c_long_isNegative(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return JS_NewBool(ctx, long_unpack(ctx, argv[0]) < 0);
}

static JSValue c_long_isZero(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return JS_NewBool(ctx, long_unpack(ctx, argv[0]) == 0);
}

static JSValue c_long_div(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return long_pack(ctx, long_unpack(ctx, argv[0]) / long_unpack(ctx, argv[1]));
}

static JSValue c_long_mod(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)argc; (void)this_val;
  return long_pack(ctx, long_unpack(ctx, argv[0]) % long_unpack(ctx, argv[1]));
}

extern "C" __attribute__((visibility("default"))) void js_intset_register_builtins(JSContext *ctx) {
  JSValue globalThis = JS_GetGlobalObject(ctx);
  JSValue fn;
  fn = JS_NewCFunction(ctx, c_long_add, "_longAdd", 2);              JS_SetPropertyStr(ctx, globalThis, "_longAdd", fn);
  fn = JS_NewCFunction(ctx, c_long_sub, "_longSub", 2);              JS_SetPropertyStr(ctx, globalThis, "_longSub", fn);
  fn = JS_NewCFunction(ctx, c_long_mul, "_longMul", 2);              JS_SetPropertyStr(ctx, globalThis, "_longMul", fn);
  fn = JS_NewCFunction(ctx, c_long_neg, "_longNeg", 1);              JS_SetPropertyStr(ctx, globalThis, "_longNeg", fn);
  fn = JS_NewCFunction(ctx, c_long_and, "_longAnd", 2);              JS_SetPropertyStr(ctx, globalThis, "_longAnd", fn);
  fn = JS_NewCFunction(ctx, c_long_or,  "_longOr",  2);              JS_SetPropertyStr(ctx, globalThis, "_longOr",  fn);
  fn = JS_NewCFunction(ctx, c_long_xor, "_longXor", 2);              JS_SetPropertyStr(ctx, globalThis, "_longXor", fn);
  fn = JS_NewCFunction(ctx, c_long_shl, "_longShl", 2);              JS_SetPropertyStr(ctx, globalThis, "_longShl", fn);
  fn = JS_NewCFunction(ctx, c_long_shr, "_longShr", 2);              JS_SetPropertyStr(ctx, globalThis, "_longShr", fn);
  fn = JS_NewCFunction(ctx, c_long_ushr, "_longUshr", 2);            JS_SetPropertyStr(ctx, globalThis, "_longUshr", fn);
  fn = JS_NewCFunction(ctx, c_long_fromInt, "_longFromInt", 1);      JS_SetPropertyStr(ctx, globalThis, "_longFromInt", fn);
  fn = JS_NewCFunction(ctx, c_long_invert, "_longInvert", 1);        JS_SetPropertyStr(ctx, globalThis, "_longInvert", fn);
  fn = JS_NewCFunction(ctx, c_long_equals, "_longEquals", 2);        JS_SetPropertyStr(ctx, globalThis, "_longEquals", fn);
  fn = JS_NewCFunction(ctx, c_long_less, "_longLess", 2);            JS_SetPropertyStr(ctx, globalThis, "_longLess", fn);
  fn = JS_NewCFunction(ctx, c_long_compare, "_longCompare", 2);      JS_SetPropertyStr(ctx, globalThis, "_longCompare", fn);
  fn = JS_NewCFunction(ctx, c_long_isNegative, "_longIsNegative", 1);JS_SetPropertyStr(ctx, globalThis, "_longIsNegative", fn);
  fn = JS_NewCFunction(ctx, c_long_isZero, "_longIsZero", 1);        JS_SetPropertyStr(ctx, globalThis, "_longIsZero", fn);
  fn = JS_NewCFunction(ctx, c_long_div, "_longDiv", 2);              JS_SetPropertyStr(ctx, globalThis, "_longDiv", fn);
  fn = JS_NewCFunction(ctx, c_long_mod, "_longMod", 2);              JS_SetPropertyStr(ctx, globalThis, "_longMod", fn);
  JS_FreeValue(ctx, globalThis);
}
