/*
 * Copyright (C) 2024 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0, Version 2.0 (the "License");
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
 * C builtins for androidx.collection.IntSet hot operations.
 *
 */
#include "quickjs/quickjs.h"
#include <stdint.h>

#define META_EMPTY 0x80
#define META_DELETED 0xFE
#define META_SENTINEL 0xFF

// Helper: extract raw int32_t* data pointer from either an ArrayBuffer or a TypedArray
// (Int32Array etc). Kotlin/JS compiles IntArray to Int32Array (a TypedArray), so we MUST
// accept both.
//
// Implementation: TypedArrays have a `.buffer` property that points to the underlying
// ArrayBuffer. For an actual ArrayBuffer, `.buffer` returns itself. So we read .buffer
// first, then JS_GetArrayBuffer on the result. This works regardless of the input type.
//
// Returns NULL and sets an exception on failure.
static int32_t* get_int32_data(JSContext *ctx, JSValueConst val, const char* arg_name) {
  // First, try to read the .buffer property. This works for both:
  //  - ArrayBuffer.buffer == the ArrayBuffer itself
  //  - TypedArray.buffer  == the underlying ArrayBuffer
  JSValue buf = JS_GetPropertyStr(ctx, val, "buffer");
  if (JS_IsException(buf)) {
    JSValue exc = JS_GetException(ctx);
    const char* str = JS_ToCString(ctx, exc);
    JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, exc);
    JS_ThrowTypeError(ctx, "%s: not an ArrayBuffer or TypedArray (no .buffer property)", arg_name);
    return NULL;
  }
  int buf_tag = JS_VALUE_GET_TAG(buf);

  size_t size;
  uint8_t* data = JS_GetArrayBuffer(ctx, &size, buf);
  JS_FreeValue(ctx, buf);
  if (!data) {
    JSValue exc = JS_GetException(ctx);
    const char* str = JS_ToCString(ctx, exc);
    JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, exc);
    return NULL;
  }
  return (int32_t*)data;
}

static inline int32_t read_int_element(JSContext *ctx, JSValueConst arr, int32_t index) {
  JSValue val = JS_GetPropertyUint32(ctx, arr, (uint32_t)index);
  if (JS_IsException(val)) {
    return -1;
  }
  int32_t result = JS_VALUE_GET_INT(val);
  JS_FreeValue(ctx, val);
  return result;
}

static inline int32_t read_meta_byte(const int32_t* flat, int32_t offset) {
  return (int32_t)((uint8_t*)flat)[offset];
}

static inline void write_meta_byte(int32_t* flat, int32_t offset, int32_t byte) {
  ((uint8_t*)flat)[offset] = (uint8_t) (byte & 0xFF);
}

static inline uint64_t load_group(const int32_t* flat, int32_t offset, int32_t capacity) {
    // Each 64‑bit word is stored as two consecutive 32‑bit ints (low word first).
    int32_t i = offset >> 3;          // word index (8 bytes per word)
    int32_t b = (offset & 0x7) << 3;  // bit offset within the word (0, 8, 16, …, 56)

    // Read two 64‑bit words without alignment issues (safe even if flat is not 8‑byte aligned).
    uint64_t lo = ((uint64_t)(uint32_t)flat[i * 2 + 1] << 32) | (uint32_t)flat[i * 2];
    uint64_t hi = ((uint64_t)(uint32_t)flat[(i + 1) * 2 + 1] << 32) | (uint32_t)flat[(i + 1) * 2];

    // Combine the two words to get the 8 bytes starting at byte offset 'offset'.
    uint64_t result;
    if (b == 0) {
        result = lo;
    } else {
        result = (lo >> b) | (hi << (64 - b));
    }
    return result;
}

// Match the 8-byte group against hash2 (0..127). Each set bit 8k+7 indicates
// that byte k matches hash2. Implementation matches the Kotlin/JS version:
//   m = (x - 0x01010101) & ~x & 0x80808080
static inline uint64_t match_hash2(uint64_t g, int32_t hash2) {
  uint64_t x = g ^ (uint64_t)(uint8_t)hash2 * 0x0101010101010101ULL;
  return (x - 0x0101010101010101ULL) & ~x & 0x8080808080808080ULL;
}

// Detect bytes equal to META_EMPTY (0x80)
static inline uint64_t mask_empty(uint64_t g) {
    // XOR with 0x80 to turn empty bytes into zero, then zero-detect
    uint64_t x = g ^ 0x8080808080808080ULL;
    return (x - 0x0101010101010101ULL) & ~x & 0x8080808080808080ULL;
}

// Detect bytes equal to META_DELETED (0xFE)
static inline uint64_t mask_deleted(uint64_t g) {
    uint64_t x = g ^ 0xFEFEFEFEFEFEFEFEULL;
    return (x - 0x0101010101010101ULL) & ~x & 0x8080808080808080ULL;
}

// Combined empty or deleted mask
static inline uint64_t mask_empty_or_deleted(uint64_t g) {
    uint64_t not_g = ~g;
    uint64_t shifted = not_g << 7;
    uint64_t temp = g & shifted;
    return temp & 0x8080808080808080ULL;
}

// Check if any empty byte exists
static inline int32_t any_empty(uint64_t g) {
    return mask_empty(g) != 0;
}

// Check if any empty or deleted byte exists
static inline int32_t any_empty_or_deleted(uint64_t g) {
    return mask_empty_or_deleted(g) != 0;
}

// Find the first empty or deleted slot in the group
static inline int32_t first_empty_or_deleted(uint64_t g, int32_t probeOffset, int32_t capacity) {
    uint64_t mask = mask_empty_or_deleted(g);
    if (mask == 0) return -1;
    int32_t bitIndex = __builtin_ctzll(mask);
    int32_t byteInGroup = bitIndex >> 3;
    return (probeOffset + byteInGroup) & capacity;   // use capacity, not capacity-1
}

// ============================================================================
// IntSet intrinsics. Elements are Int32 values stored in IntArray. We read
// and compare them directly as int32_t.
// ============================================================================

static JSValue c_intset_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=elements, argv[2]=capacity, argv[3]=element, argv[4]=hash, argv[5]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t element = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int32_t slotInt = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotInt == element) {
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty_or_deleted(g)) {
            break;
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    return JS_NewInt32(ctx, -1);
}

static int32_t find_first_available_slot(const int32_t* meta, int32_t capacity, int32_t hash1);

static JSValue c_intset_find_slot(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t element = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    int32_t* emptySlot = get_int32_data(ctx, argv[6], "emptySlot");
    if (!emptySlot) return JS_EXCEPTION;

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            int32_t slotInt = read_int_element(ctx, argv[1], index);
            if (slotInt == element) {
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) {
            break;
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    emptySlot[0] = find_first_available_slot(meta, capacity, ((uint32_t)hash >> 7) & mask);
    return JS_NewInt32(ctx, -1);
}

static JSValue c_intset_remove(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=elements, argv[2]=capacity, argv[3]=element, argv[4]=hash, argv[5]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t element = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int32_t slotInt = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotInt == element) {
                write_meta_byte(meta, index, META_DELETED);
                JS_SetPropertyUint32(ctx, argv[1], (uint32_t)index, JS_NULL);
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) break;
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    return JS_NewInt32(ctx, -1);
}

// ============================================================================
// ScatterSet intrinsics. Same metadata layout as IntSet, but `elements` holds
// JS values (Any?) instead of Int. We compare with JS_StrictEq (===) which matches
// Kotlin's `==` on Any?.
//
// JS signature: _scatterSetFind(metadataFlat, elements, capacity, element, hash, hash2)
// Returns index >= 0, or -1 if not found.
// JS signature: _scatterSetFindAvailableSlot(metadataFlat, capacity, hash1)
// Returns index of first Empty/Deleted slot, or -1 if none found.
// ============================================================================

// ============================================================================
// ScatterSet intrinsics (fixed)
// ============================================================================

// Helper: Kotlin-compatible equality for Any?
// Matches JS equals function behavior:
static int kotlin_equals(JSContext *ctx, JSValueConst a, JSValueConst b) {
    // null/undefined check (JS: obj1 == null)
    int aIsNullish = JS_IsNull(a) || JS_IsUndefined(a);
    int bIsNullish = JS_IsNull(b) || JS_IsUndefined(b);
    if (aIsNullish) {
        return bIsNullish ? 1 : 0;
    }
    if (bIsNullish) {
        return 0;
    }

    // Object with equals method
    JSValue equals = JS_GetPropertyStr(ctx, a, "equals");
    if (!JS_IsException(equals) && JS_IsFunction(ctx, equals)) {
        JSValue args[1] = { JS_DupValue(ctx, b) };
        JSValue result = JS_Call(ctx, equals, a, 1, args);
        JS_FreeValue(ctx, args[0]);
        JS_FreeValue(ctx, equals);
        if (!JS_IsException(result)) {
            // Check if result is JS false - need to check both tag and value
            // JS_FALSE = JS_MKVAL(JS_TAG_BOOL, 0)
            // In QuickJS, JSValue is compared via JS_StrictEq or by checking components
            int ret = !(JS_VALUE_GET_TAG(result) == JS_TAG_BOOL && JS_VALUE_GET_BOOL(result) == 0);
            JS_FreeValue(ctx, result);
            return ret;
        }
        JS_FreeValue(ctx, result);
    } else {
        JS_FreeValue(ctx, equals);
    }

    // Primitives / non-objects: use strict equality
    if (JS_VALUE_GET_TAG(a) != JS_TAG_OBJECT) {
        // Numbers: handle NaN and -0/+0
        if (JS_IsNumber(a) && JS_IsNumber(b)) {
            double numA, numB;
            JS_ToFloat64(ctx, &numA, a);
            JS_ToFloat64(ctx, &numB, b);
            // NaN check: NaN !== NaN in JS
            if (numA != numA) { // numA is NaN
                return numB != numB ? 1 : 0;
            }
            // -0/+0 check: 1/0 === 1/-0 is false in JS
            if (numA == numB) {
                if (numA != 0) return 1; // both non-zero and equal
                // both are 0, check sign
                double invA = 1.0 / numA;
                double invB = 1.0 / numB;
                return (invA == invB) ? 1 : 0;
            }
            return 0;
        }
        return JS_StrictEq(ctx, a, b);
    }

    // Objects: reference equality (JS: obj1 === obj2)
    return JS_StrictEq(ctx, a, b);
}

// ============================================================================
// ScatterSet intrinsics (fixed)
// ============================================================================

static JSValue c_scatterset_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    JSValueConst element = argv[3];
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

    int32_t mask = capacity;                       // use capacity (not capacity-1)
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, element);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty_or_deleted(g)) {
            break;
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    return JS_NewInt32(ctx, -1);
}

static int32_t find_first_available_slot(const int32_t* meta, int32_t capacity, int32_t hash1);

static JSValue c_scatterset_find_slot(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    JSValueConst element = argv[3];
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    int32_t* emptySlot = get_int32_data(ctx, argv[6], "emptySlot");
    if (!emptySlot) return JS_EXCEPTION;

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, element);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) {
            break;
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    emptySlot[0] = find_first_available_slot(meta, capacity, ((uint32_t)hash >> 7) & mask);
    return JS_NewInt32(ctx, -1);
}

static int32_t find_first_available_slot(const int32_t* meta, int32_t capacity, int32_t hash1) {
    int32_t mask = capacity;
    int32_t probeOffset = hash1 & mask;
    int32_t probeIndex = 0;
    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = mask_empty_or_deleted(g);
        if (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            return (probeOffset + byteInGroup) & mask;
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
}

static JSValue c_scatterset_remove(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    JSValueConst element = argv[3];
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, element);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                // Mark as Deleted and clear the element to null (not undefined)
                write_meta_byte(meta, index, META_DELETED);
                JS_SetPropertyUint32(ctx, argv[1], (uint32_t)index, JS_NULL);
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) break;
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    return JS_NewInt32(ctx, -1);
}

static JSValue c_scattermap_find_slot(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    JSValueConst key = argv[3];
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    int32_t* emptySlot = get_int32_data(ctx, argv[6], "emptySlot");
    if (!emptySlot) return JS_EXCEPTION;

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, key);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) {
            break;
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    emptySlot[0] = find_first_available_slot(meta, capacity, ((uint32_t)hash >> 7) & mask);
    return JS_NewInt32(ctx, -1);
}

static JSValue c_scattermap_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=keys, argv[2]=capacity, argv[3]=key, argv[4]=hash, argv[5]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    JSValueConst key = argv[3];
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, key);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty_or_deleted(g)) {
            break;
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    return JS_NewInt32(ctx, -1);
}

static JSValue c_scattermap_remove(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[1]=keys, argv[2]=values, argv[3]=capacity, argv[4]=key, argv[5]=hash, argv[6]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[3]);
    JSValueConst key = argv[4];
    int32_t hash = JS_VALUE_GET_INT(argv[5]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[6]);

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, key);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                write_meta_byte(meta, index, META_DELETED);
                JS_SetPropertyUint32(ctx, argv[1], (uint32_t)index, JS_NULL);
                JS_SetPropertyUint32(ctx, argv[2], (uint32_t)index, JS_NULL);
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) break;
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    return JS_NewInt32(ctx, -1);
}

// ============================================================================
// IntObjectMap intrinsics. Keys are Int32. For keys we compare directly as int32_t.
// ============================================================================

static JSValue c_int_object_map_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=keys, argv[2]=capacity, argv[3]=key, argv[4]=hash, argv[5]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t key = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int32_t slotKey = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotKey == key) {
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty_or_deleted(g)) {
            break;
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    return JS_NewInt32(ctx, -1);
}

static JSValue c_int_object_map_find_slot(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=keys, argv[2]=capacity, argv[3]=key, argv[4]=hash, argv[5]=hash2, argv[6]=emptySlot
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t key = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    int32_t* emptySlot = get_int32_data(ctx, argv[6], "emptySlot");
    if (!emptySlot) return JS_EXCEPTION;

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int32_t slotKey = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotKey == key) {
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) {
            break;
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    emptySlot[0] = find_first_available_slot(meta, capacity, ((uint32_t)hash >> 7) & mask);
    return JS_NewInt32(ctx, -1);
}

static JSValue c_int_object_map_put(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=keys, argv[2]=capacity, argv[3]=key, argv[4]=hash, argv[5]=hash2,
    // argv[6]=outCreated, argv[7]=outSizeDelta
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t key = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    int32_t* outCreated = get_int32_data(ctx, argv[6], "outCreated");
    if (!outCreated) return JS_EXCEPTION;
    int32_t* outSizeDelta = get_int32_data(ctx, argv[7], "outSizeDelta");
    if (!outSizeDelta) return JS_EXCEPTION;

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int32_t slotKey = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotKey == key) {
                outCreated[0] = 0;
                outSizeDelta[0] = 0;
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) {
            int32_t bitIdx = __builtin_ctzll(mask_empty(g));
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            write_meta_byte(meta, index, hash2);
            JS_SetPropertyUint32(ctx, argv[1], (uint32_t)index, JS_NewInt32(ctx, key));
            outCreated[0] = 1;
            outSizeDelta[0] = 1;
            return JS_NewInt32(ctx, index);
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
}

static JSValue c_int_object_map_remove(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=keys, argv[2]=capacity, argv[3]=key, argv[4]=hash, argv[5]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t key = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

    int32_t mask = capacity;
    int32_t probeOffset = ((uint32_t)hash >> 7) & mask;
    int32_t probeIndex = 0;

    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = match_hash2(g, hash2);
        while (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
            if (JS_IsException(slotVal)) {
                return JS_EXCEPTION;
            }
            int32_t slotKey = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotKey == key) {
                write_meta_byte(meta, index, META_DELETED);
                JS_SetPropertyUint32(ctx, argv[1], (uint32_t)index, JS_NULL);
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) break;
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    return JS_NewInt32(ctx, -1);
}

static JSValue c_int_object_map_find_available_slot(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=capacity, argv[2]=hash1
    int32_t capacity = JS_VALUE_GET_INT(argv[1]);
    int32_t hash1 = JS_VALUE_GET_INT(argv[2]);

    int32_t mask = capacity;
    int32_t probeOffset = hash1 & mask;
    int32_t probeIndex = 0;
    while (1) {
        uint64_t g = load_group(meta, probeOffset, capacity);
        uint64_t m = mask_empty_or_deleted(g);
        if (m != 0) {
            int32_t bitIdx = __builtin_ctzll(m);
            int32_t byteInGroup = bitIdx >> 3;
            int32_t result = (probeOffset + byteInGroup) & mask;
            return JS_NewInt32(ctx, result);
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
}

// Generic JS-callable log function. Allows Kotlin/JS code to send debug messages that
// appear in Android logcat (and stderr on other platforms).
static JSValue c_dbg_log(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)this_val; (void)argc;
  if (argc < 1) return JS_UNDEFINED;
  const char* str = JS_ToCString(ctx, argv[0]);
  if (str) {
    JS_FreeCString(ctx, str);
  }
  return JS_UNDEFINED;
}

// Array copy: copies elements from source to destination
// argv[0]=source, argv[1]=destination, argv[2]=destinationOffset, argv[3]=startIndex, argv[4]=endIndex
static JSValue c_array_copy(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    if (argc < 5) return JS_EXCEPTION;

    JSValueConst source = argv[0];
    JSValueConst destination = argv[1];
    int32_t destOffset = JS_VALUE_GET_INT(argv[2]);
    int32_t startIndex = JS_VALUE_GET_INT(argv[3]);
    int32_t endIndex = JS_VALUE_GET_INT(argv[4]);
    int32_t rangeSize = endIndex - startIndex;

    int isSourceArray = JS_IsArray(ctx, source);
    int isDestArray = JS_IsArray(ctx, destination);

    JSValue srcBuffer = JS_GetPropertyStr(ctx, source, "buffer");
    JSValue dstBuffer = JS_GetPropertyStr(ctx, destination, "buffer");
    int srcBufferIsObject = !JS_IsException(srcBuffer) && !JS_IsNull(srcBuffer);
    int dstBufferIsObject = !JS_IsException(dstBuffer) && !JS_IsNull(dstBuffer);
    JS_FreeValue(ctx, srcBuffer);
    JS_FreeValue(ctx, dstBuffer);

    int isSourceTypedArray = !isSourceArray && srcBufferIsObject;
    int isDestTypedArray = !isDestArray && dstBufferIsObject;

    if (isSourceTypedArray && isDestTypedArray) {
        JSValue subarrayFn = JS_GetPropertyStr(ctx, source, "subarray");
        JSValue setFn = JS_GetPropertyStr(ctx, destination, "set");

        if (!JS_IsException(subarrayFn) && !JS_IsException(setFn)) {
            JSValue subarray = JS_Call(ctx, subarrayFn, source, 2, (JSValue[]){ JS_NewInt32(ctx, startIndex), JS_NewInt32(ctx, endIndex) });
            JS_FreeValue(ctx, subarrayFn);
            if (!JS_IsException(subarray)) {
                JSValue result = JS_Call(ctx, setFn, destination, 2, (JSValue[]){ subarray, JS_NewInt32(ctx, destOffset) });
                JS_FreeValue(ctx, subarray);
                JS_FreeValue(ctx, setFn);
                if (!JS_IsException(result)) return result;
            }
            JS_GetException(ctx);
        }
        if (!JS_IsException(subarrayFn)) JS_FreeValue(ctx, subarrayFn);
        if (!JS_IsException(setFn)) JS_FreeValue(ctx, setFn);
    }

    int sameArray = JS_StrictEq(ctx, source, destination);
    if (sameArray && destOffset > startIndex) {
        for (int32_t i = rangeSize - 1; i >= 0; i--) {
            JSValue val = JS_GetPropertyUint32(ctx, source, (uint32_t)(startIndex + i));
            if (JS_IsException(val)) return val;
            JS_SetPropertyUint32(ctx, destination, (uint32_t)(destOffset + i), val);
        }
    } else {
        for (int32_t i = 0; i < rangeSize; i++) {
            JSValue val = JS_GetPropertyUint32(ctx, source, (uint32_t)(startIndex + i));
            if (JS_IsException(val)) return val;
            JS_SetPropertyUint32(ctx, destination, (uint32_t)(destOffset + i), val);
        }
    }
    return JS_UNDEFINED;
}

// Computes Kotlin's String.hashCode(): hash = s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
// which is implemented as iterative: hash = hash * 31 + charAt(i)
// JS charCodeAt returns UTF-16 code units (16-bit values)
static JSValue c_get_string_hash_code(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    if (argc < 1) return JS_EXCEPTION;

    JSValueConst str = argv[0];

    // Convert JS string to UTF-8 C string, then get length
    const char* cstr = JS_ToCString(ctx, str);
    if (!cstr) return JS_NewInt32(ctx, 0);

    // Get byte length of UTF-8 string
    size_t byteLen = 0;
    while (cstr[byteLen] != 0) byteLen++;

    if (byteLen == 0) {
        JS_FreeCString(ctx, cstr);
        return JS_NewInt32(ctx, 0);
    }

    // Decode UTF-8 to UTF-16 and compute hash
    // This matches JS charCodeAt behavior: each char is a 16-bit UTF-16 code unit
    int32_t hash = 0;
    size_t i = 0;
    while (i < byteLen) {
        uint32_t code;
        // Decode UTF-8 character
        if ((cstr[i] & 0x80) == 0) {
            // 1-byte sequence (ASCII)
            code = (uint8_t)cstr[i];
            i += 1;
        } else if ((cstr[i] & 0xE0) == 0xC0) {
            // 2-byte sequence
            code = ((uint8_t)cstr[i] & 0x1F) << 6;
            code |= ((uint8_t)cstr[i + 1] & 0x3F);
            i += 2;
        } else if ((cstr[i] & 0xF0) == 0xE0) {
            // 3-byte sequence
            code = ((uint8_t)cstr[i] & 0x0F) << 12;
            code |= ((uint8_t)cstr[i + 1] & 0x3F) << 6;
            code |= ((uint8_t)cstr[i + 2] & 0x3F);
            i += 3;
        } else {
            // 4-byte sequence (surrogate pair in UTF-16)
            // For simplicity, just use the raw bytes combined
            code = ((uint8_t)cstr[i] & 0x07) << 18;
            code |= ((uint8_t)cstr[i + 1] & 0x3F) << 12;
            code |= ((uint8_t)cstr[i + 2] & 0x3F) << 6;
            code |= ((uint8_t)cstr[i + 3] & 0x3F);
            i += 4;
        }
        // hash * 31 + char, with 32-bit signed integer overflow (same as JS.imul)
        hash = (int32_t)((int32_t)hash * 31 + (int32_t)code);
    }

    JS_FreeCString(ctx, cstr);
    return JS_NewInt32(ctx, hash);
}

extern "C" __attribute__((visibility("default"))) void js_intset_register_builtins(JSContext *ctx) {
  JSValue globalThis = JS_GetGlobalObject(ctx);
  JSValue fn;
  // IntSet intrinsics
  fn = JS_NewCFunction(ctx, c_intset_find,                      "_intsetFind",                      6);
  JS_SetPropertyStr(ctx, globalThis, "_intsetFind", fn);
  fn = JS_NewCFunction(ctx, c_intset_find_slot,                 "_intsetFindSlot",                  7);
  JS_SetPropertyStr(ctx, globalThis, "_intsetFindSlot", fn);
  fn = JS_NewCFunction(ctx, c_intset_remove,                     "_intsetRemove",                    6);
  JS_SetPropertyStr(ctx, globalThis, "_intsetRemove", fn);
  // IntObjectMap intrinsics
  fn = JS_NewCFunction(ctx, c_int_object_map_find,               "_intObjectMapFind",                6);
  JS_SetPropertyStr(ctx, globalThis, "_intObjectMapFind", fn);
  fn = JS_NewCFunction(ctx, c_int_object_map_find_slot,         "_intObjectMapFindSlot",            7);
  JS_SetPropertyStr(ctx, globalThis, "_intObjectMapFindSlot", fn);
  fn = JS_NewCFunction(ctx, c_int_object_map_put,                "_intObjectMapPut",                 8);
  JS_SetPropertyStr(ctx, globalThis, "_intObjectMapPut", fn);
  fn = JS_NewCFunction(ctx, c_int_object_map_remove,              "_intObjectMapRemove",              6);
  JS_SetPropertyStr(ctx, globalThis, "_intObjectMapRemove", fn);
  fn = JS_NewCFunction(ctx, c_int_object_map_find_available_slot, "_intObjectMapFindAvailableSlot",   3);
  JS_SetPropertyStr(ctx, globalThis, "_intObjectMapFindAvailableSlot", fn);
  // ScatterSet intrinsics
  fn = JS_NewCFunction(ctx, c_scatterset_find,                    "_scatterSetFind",                  6);
  JS_SetPropertyStr(ctx, globalThis, "_scatterSetFind", fn);
  fn = JS_NewCFunction(ctx, c_scatterset_find_slot, "_scatterSetFindSlot", 7);
  JS_SetPropertyStr(ctx, globalThis, "_scatterSetFindSlot", fn);
  fn = JS_NewCFunction(ctx, c_scatterset_remove,                 "_scatterSetRemove",                6);
  JS_SetPropertyStr(ctx, globalThis, "_scatterSetRemove", fn);
  // ScatterMap intrinsics
  fn = JS_NewCFunction(ctx, c_scattermap_find_slot, "_scatterMapFindSlot", 7);
  JS_SetPropertyStr(ctx, globalThis, "_scatterMapFindSlot", fn);
  fn = JS_NewCFunction(ctx, c_scattermap_find, "_scatterMapFind", 6);
  JS_SetPropertyStr(ctx, globalThis, "_scatterMapFind", fn);
  fn = JS_NewCFunction(ctx, c_scattermap_remove, "_scatterMapRemove", 7);
  JS_SetPropertyStr(ctx, globalThis, "_scatterMapRemove", fn);
  // Generic debug log helper for Kotlin/JS code to log to logcat.
  fn = JS_NewCFunction(ctx, c_dbg_log,                           "_dbg",                              1);
  JS_SetPropertyStr(ctx, globalThis, "_dbg", fn);
  // String hashCode helper
  fn = JS_NewCFunction(ctx, c_get_string_hash_code,              "_getStringHashCode",                1);
  JS_SetPropertyStr(ctx, globalThis, "_getStringHashCode", fn);
  // Array copy helper
  fn = JS_NewCFunction(ctx, c_array_copy,                         "_arrayCopy",                        5);
  JS_SetPropertyStr(ctx, globalThis, "_arrayCopy", fn);
  JS_FreeValue(ctx, globalThis);
}
