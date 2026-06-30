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
 * Data layout: each IntSet carries a parallel Int32Array `metadataFlat` with
 * length equal to the rounded-up metadata byte count. Byte i of the flat
 * array is the metadata byte for slot i, stored in the low 8 bits of flat[i].
 *
 * Metadata byte values (matching ScatterMap.kt):
 *   0x80 (128)  = Empty
 *   0xFE (254)  = Deleted
 *   0xFF (255)  = Sentinel
 *   0x00..0x7F  = Full, holds hash2 of the element at that slot
 *
 * Function signatures (all return JS_NewInt32):
 *   _intsetFind(metadataFlat, elements, capacity, element, hash, hash2)
 *       -> index >= 0 if found, else -1
 *   _intsetAdd(metadataFlat, elements, capacity, element, hash, hash2, outCreated, outSizeDelta)
 *       -> index (existing or new), outCreated[0] = 0|1, outSizeDelta[0] = 0|1
 *   _intsetRemove(metadataFlat, elements, capacity, element, hash, hash2)
 *       -> removed index, or -1 if not present
 *
 * The Kotlin/JS side keeps `metadataFlat` in sync with `metadata` whenever
 * the latter is mutated (via the new writeRawMetadataFlat primitive).
 */
#include "quickjs/quickjs.h"
#include <stdint.h>

#ifdef __ANDROID__
#include <android/log.h>
#define DBG_TAG "IntSetBuiltin"
#define DBG_LOGI(...) __android_log_print(ANDROID_LOG_INFO, DBG_TAG, __VA_ARGS__)
#define DBG_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, DBG_TAG, __VA_ARGS__)
#else
#include <stdio.h>
#define DBG_LOGI(...) do { fprintf(stderr, "[IntSetBuiltin] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while (0)
#define DBG_LOGE(...) do { fprintf(stderr, "[IntSetBuiltin ERROR] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr); } while (0)
#endif

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
    DBG_LOGI("c_intset_find ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=elements, argv[2]=capacity, argv[3]=element, argv[4]=hash, argv[5]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t element = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_intset_find cap=%d elem=%d hash=%d hash2=%d", capacity, element, hash, hash2);

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
                DBG_LOGI("c_intset_find EXCEPTION");
                return JS_EXCEPTION;
            }
            int32_t slotInt = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotInt == element) {
                DBG_LOGI("c_intset_find FOUND index=%d", index);
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
    DBG_LOGI("c_intset_find NOT_FOUND");
    return JS_NewInt32(ctx, -1);
}

static JSValue c_intset_add(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_intset_add ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=elements, argv[2]=capacity, argv[3]=element, argv[4]=hash, argv[5]=hash2,
    // argv[6]=outCreated, argv[7]=outSizeDelta
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t element = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_intset_add cap=%d elem=%d hash=%d hash2=%d", capacity, element, hash, hash2);
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
                DBG_LOGI("c_intset_add EXCEPTION");
                return JS_EXCEPTION;
            }
            int32_t slotInt = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotInt == element) {
                outCreated[0] = 0;
                outSizeDelta[0] = 0;
                DBG_LOGI("c_intset_add EXISTS index=%d", index);
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) {
            int32_t bitIdx = __builtin_ctzll(mask_empty(g));
            int32_t byteInGroup = bitIdx >> 3;
            int32_t index = (probeOffset + byteInGroup) & mask;
            write_meta_byte(meta, index, hash2);
            JS_SetPropertyUint32(ctx, argv[1], (uint32_t)index, JS_NewInt32(ctx, element));
            outCreated[0] = 1;
            outSizeDelta[0] = 1;
            DBG_LOGI("c_intset_add INSERTED index=%d", index);
            return JS_NewInt32(ctx, index);
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
}

static JSValue c_intset_remove(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_intset_remove ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=elements, argv[2]=capacity, argv[3]=element, argv[4]=hash, argv[5]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t element = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_intset_remove cap=%d elem=%d hash=%d hash2=%d", capacity, element, hash, hash2);

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
                DBG_LOGI("c_intset_remove EXCEPTION");
                return JS_EXCEPTION;
            }
            int32_t slotInt = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotInt == element) {
                write_meta_byte(meta, index, META_DELETED);
                JS_SetPropertyUint32(ctx, argv[1], (uint32_t)index, JS_NULL);
                DBG_LOGI("c_intset_remove REMOVED index=%d", index);
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) break;
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    DBG_LOGI("c_intset_remove NOT_FOUND");
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

// Helper: call kotlin.equals(a, b) from C
// Helper: Kotlin-compatible equality for Any?
static int kotlin_equals(JSContext *ctx, JSValueConst a, JSValueConst b) {
    // Primitives, null, undefined: use strict equality
    if (JS_VALUE_GET_TAG(a) != JS_TAG_OBJECT) {
        return JS_StrictEq(ctx, a, b);
    }

    // a is an object: try a.equals(b)
    JSValue equals = JS_GetPropertyStr(ctx, a, "equals");
    if (JS_IsException(equals)) {
        JS_FreeValue(ctx, equals);
        return 0;
    }
    if (JS_VALUE_GET_TAG(equals) != JS_TAG_OBJECT) {
        JS_FreeValue(ctx, equals);
        return 0; // No equals method → not equal
    }

    JSValue args[1] = { JS_DupValue(ctx, b) };
    JSValue result = JS_Call(ctx, equals, a, 1, args);
    JS_FreeValue(ctx, args[0]);
    JS_FreeValue(ctx, equals);
    if (JS_IsException(result)) {
        JS_FreeValue(ctx, result);
        return 0;
    }
    int ret = JS_VALUE_GET_BOOL(result);
    JS_FreeValue(ctx, result);
    return ret;
}

// ============================================================================
// ScatterSet intrinsics (fixed)
// ============================================================================

static JSValue c_scatterset_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_scatterset_find ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    JSValueConst element = argv[3];
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_scatterset_find cap=%d hash=%d hash2=%d", capacity, hash, hash2);

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
                DBG_LOGI("c_scatterset_find EXCEPTION");
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, element);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                DBG_LOGI("c_scatterset_find FOUND index=%d", index);
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
    DBG_LOGI("c_scatterset_find NOT_FOUND");
    return JS_NewInt32(ctx, -1);
}

static int32_t find_first_available_slot(const int32_t* meta, int32_t capacity, int32_t hash1);

static JSValue c_scatterset_find_slot(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_scatterset_find_slot ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    JSValueConst element = argv[3];
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_scatterset_find_slot cap=%d hash=%d hash2=%d", capacity, hash, hash2);
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
                DBG_LOGI("c_scatterset_find_slot EXCEPTION");
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, element);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                DBG_LOGI("c_scatterset_find_slot FOUND index=%d", index);
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
    DBG_LOGI("c_scatterset_find_slot NOT_FOUND emptySlot=%d", emptySlot[0]);
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
    DBG_LOGI("c_scatterset_remove ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    JSValueConst element = argv[3];
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_scatterset_remove cap=%d hash=%d hash2=%d", capacity, hash, hash2);

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
                DBG_LOGI("c_scatterset_remove EXCEPTION");
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, element);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                // Mark as Deleted and clear the element to null (not undefined)
                write_meta_byte(meta, index, META_DELETED);
                JS_SetPropertyUint32(ctx, argv[1], (uint32_t)index, JS_NULL);
                DBG_LOGI("c_scatterset_remove REMOVED index=%d", index);
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) break;
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    DBG_LOGI("c_scatterset_remove NOT_FOUND");
    return JS_NewInt32(ctx, -1);
}

static JSValue c_scattermap_find_slot(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_scattermap_find_slot ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    JSValueConst key = argv[3];
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_scattermap_find_slot cap=%d hash=%d hash2=%d", capacity, hash, hash2);
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
                DBG_LOGI("c_scattermap_find_slot EXCEPTION");
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, key);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                DBG_LOGI("c_scattermap_find_slot FOUND index=%d", index);
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
    DBG_LOGI("c_scattermap_find_slot NOT_FOUND emptySlot=%d", emptySlot[0]);
    return JS_NewInt32(ctx, -1);
}

static JSValue c_scattermap_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_scattermap_find ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=keys, argv[2]=capacity, argv[3]=key, argv[4]=hash, argv[5]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    JSValueConst key = argv[3];
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_scattermap_find cap=%d hash=%d hash2=%d", capacity, hash, hash2);

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
                DBG_LOGI("c_scattermap_find EXCEPTION");
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, key);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                DBG_LOGI("c_scattermap_find FOUND index=%d", index);
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
    DBG_LOGI("c_scattermap_find NOT_FOUND");
    return JS_NewInt32(ctx, -1);
}

static JSValue c_scattermap_remove(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_scattermap_remove ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[1]=keys, argv[2]=values, argv[3]=capacity, argv[4]=key, argv[5]=hash, argv[6]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[3]);
    JSValueConst key = argv[4];
    int32_t hash = JS_VALUE_GET_INT(argv[5]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[6]);
    DBG_LOGI("c_scattermap_remove cap=%d hash=%d hash2=%d", capacity, hash, hash2);

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
                DBG_LOGI("c_scattermap_remove EXCEPTION");
                return JS_EXCEPTION;
            }
            int eq = kotlin_equals(ctx, slotVal, key);
            JS_FreeValue(ctx, slotVal);
            if (eq) {
                write_meta_byte(meta, index, META_DELETED);
                JS_SetPropertyUint32(ctx, argv[1], (uint32_t)index, JS_NULL);
                JS_SetPropertyUint32(ctx, argv[2], (uint32_t)index, JS_NULL);
                DBG_LOGI("c_scattermap_remove REMOVED index=%d", index);
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) break;
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    DBG_LOGI("c_scattermap_remove NOT_FOUND");
    return JS_NewInt32(ctx, -1);
}

// ============================================================================
// IntObjectMap intrinsics. Keys are Int32. For keys we compare directly as int32_t.
// ============================================================================

static JSValue c_int_object_map_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_int_object_map_find ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=keys, argv[2]=capacity, argv[3]=key, argv[4]=hash, argv[5]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t key = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_int_object_map_find cap=%d key=%d hash=%d hash2=%d", capacity, key, hash, hash2);

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
                DBG_LOGI("c_int_object_map_find EXCEPTION");
                return JS_EXCEPTION;
            }
            int32_t slotKey = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotKey == key) {
                DBG_LOGI("c_int_object_map_find FOUND index=%d", index);
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
    DBG_LOGI("c_int_object_map_find NOT_FOUND");
    return JS_NewInt32(ctx, -1);
}

static JSValue c_int_object_map_put(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_int_object_map_put ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=keys, argv[2]=capacity, argv[3]=key, argv[4]=hash, argv[5]=hash2,
    // argv[6]=outCreated, argv[7]=outSizeDelta
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t key = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_int_object_map_put cap=%d key=%d hash=%d hash2=%d", capacity, key, hash, hash2);
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
                DBG_LOGI("c_int_object_map_put EXCEPTION");
                return JS_EXCEPTION;
            }
            int32_t slotKey = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotKey == key) {
                outCreated[0] = 0;
                outSizeDelta[0] = 0;
                DBG_LOGI("c_int_object_map_put EXISTS index=%d", index);
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
            DBG_LOGI("c_int_object_map_put INSERTED index=%d", index);
            return JS_NewInt32(ctx, index);
        }
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
}

static JSValue c_int_object_map_remove(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_int_object_map_remove ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=keys, argv[2]=capacity, argv[3]=key, argv[4]=hash, argv[5]=hash2
    int32_t capacity = JS_VALUE_GET_INT(argv[2]);
    int32_t key = JS_VALUE_GET_INT(argv[3]);
    int32_t hash = JS_VALUE_GET_INT(argv[4]);
    int32_t hash2 = JS_VALUE_GET_INT(argv[5]);
    DBG_LOGI("c_int_object_map_remove cap=%d key=%d hash=%d hash2=%d", capacity, key, hash, hash2);

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
                DBG_LOGI("c_int_object_map_remove EXCEPTION");
                return JS_EXCEPTION;
            }
            int32_t slotKey = JS_VALUE_GET_INT(slotVal);
            JS_FreeValue(ctx, slotVal);
            if (slotKey == key) {
                write_meta_byte(meta, index, META_DELETED);
                JS_SetPropertyUint32(ctx, argv[1], (uint32_t)index, JS_NULL);
                DBG_LOGI("c_int_object_map_remove REMOVED index=%d", index);
                return JS_NewInt32(ctx, index);
            }
            m &= m - 1;
        }
        if (any_empty(g)) break;
        probeIndex += 8;
        probeOffset = (probeOffset + probeIndex) & mask;
    }
    DBG_LOGI("c_int_object_map_remove NOT_FOUND");
    return JS_NewInt32(ctx, -1);
}

static JSValue c_int_object_map_find_available_slot(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc;
    DBG_LOGI("c_int_object_map_find_available_slot ENTRY");
    int32_t* meta = get_int32_data(ctx, argv[0], "meta");
    if (!meta) return JS_EXCEPTION;
    // argv[0]=meta, argv[1]=capacity, argv[2]=hash1
    int32_t capacity = JS_VALUE_GET_INT(argv[1]);
    int32_t hash1 = JS_VALUE_GET_INT(argv[2]);
    DBG_LOGI("c_int_object_map_find_available_slot cap=%d hash1=%d", capacity, hash1);

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
            DBG_LOGI("c_int_object_map_find_available_slot FOUND index=%d", result);
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
    DBG_LOGI("[JS] %s", str);
    JS_FreeCString(ctx, str);
  }
  return JS_UNDEFINED;
}

extern "C" __attribute__((visibility("default"))) void js_intset_register_builtins(JSContext *ctx) {
  JSValue globalThis = JS_GetGlobalObject(ctx);
  JSValue fn;
  // IntSet intrinsics
  fn = JS_NewCFunction(ctx, c_intset_find,                      "_intsetFind",                      6);
  JS_SetPropertyStr(ctx, globalThis, "_intsetFind", fn);
  fn = JS_NewCFunction(ctx, c_intset_add,                        "_intsetAdd",                       8);
  JS_SetPropertyStr(ctx, globalThis, "_intsetAdd", fn);
  fn = JS_NewCFunction(ctx, c_intset_remove,                     "_intsetRemove",                    6);
  JS_SetPropertyStr(ctx, globalThis, "_intsetRemove", fn);
  // IntObjectMap intrinsics
  fn = JS_NewCFunction(ctx, c_int_object_map_find,               "_intObjectMapFind",                6);
  JS_SetPropertyStr(ctx, globalThis, "_intObjectMapFind", fn);
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
  JS_FreeValue(ctx, globalThis);
}
