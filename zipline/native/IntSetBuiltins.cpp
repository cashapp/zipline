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
// #define DBG_LOGI(...) __android_log_print(ANDROID_LOG_INFO, DBG_TAG, __VA_ARGS__)
// #define DBG_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, DBG_TAG, __VA_ARGS__)
#define DBG_LOGI(...) do {} while (0)
#define DBG_LOGE(...) do {} while (0)
#else
#define DBG_LOGI(...) do {} while (0)
#define DBG_LOGE(...) do {} while (0)
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
  // [DBG] Log entry
  int input_tag = JS_VALUE_GET_TAG(val);
  DBG_LOGI("get_int32_data(%s) input tag=%d", arg_name, input_tag);

  // First, try to read the .buffer property. This works for both:
  //  - ArrayBuffer.buffer == the ArrayBuffer itself
  //  - TypedArray.buffer  == the underlying ArrayBuffer
  JSValue buf = JS_GetPropertyStr(ctx, val, "buffer");
  if (JS_IsException(buf)) {
    DBG_LOGE("get_int32_data(%s) FAILED: JS_GetPropertyStr(.buffer) threw", arg_name);
    JSValue exc = JS_GetException(ctx);
    const char* str = JS_ToCString(ctx, exc);
    DBG_LOGE("  exception: %s", str ? str : "(null)");
    JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, exc);
    JS_ThrowTypeError(ctx, "%s: not an ArrayBuffer or TypedArray (no .buffer property)", arg_name);
    return NULL;
  }
  int buf_tag = JS_VALUE_GET_TAG(buf);
  DBG_LOGI("get_int32_data(%s) .buffer tag=%d", arg_name, buf_tag);

  size_t size;
  uint8_t* data = JS_GetArrayBuffer(ctx, &size, buf);
  JS_FreeValue(ctx, buf);
  if (!data) {
    DBG_LOGE("get_int32_data(%s) FAILED: JS_GetArrayBuffer returned NULL", arg_name);
    JSValue exc = JS_GetException(ctx);
    const char* str = JS_ToCString(ctx, exc);
    DBG_LOGE("  exception: %s", str ? str : "(null)");
    JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, exc);
    return NULL;
  }
  DBG_LOGI("get_int32_data(%s) OK: data=%p size=%zu", arg_name, data, size);
  return (int32_t*)data;
}

static inline int32_t read_meta_byte(const int32_t* flat, int32_t offset) {
  return flat[offset] & 0xFF;
}

static inline void write_meta_byte(int32_t* flat, int32_t offset, int32_t byte) {
  flat[offset] = byte & 0xFF;
}

// Build a uint64 with byte 0..7 = flat[offset]..flat[offset+7] (wrapping mod capacity).
// capacity must be a power of 2 (>= 8).
static inline uint64_t load_group(const int32_t* flat, int32_t offset, int32_t capacity) {
  uint64_t g = 0;
  int32_t mask = capacity - 1;
  for (int i = 0; i < 8; i++) {
    int32_t o = (offset + i) & mask;
    g |= ((uint64_t)(uint8_t)read_meta_byte(flat, o)) << (i * 8);
  }
  return g;
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
    return mask_empty(g) | mask_deleted(g);
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
    int32_t bitIndex = __builtin_ctzll(mask);          // index of first set bit
    int32_t byteInGroup = bitIndex >> 3;               // bitIndex / 8
    int32_t maskIdx = capacity - 1;
    return (probeOffset + byteInGroup) & maskIdx;
}

static JSValue c_intset_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)this_val; (void)argc;
  int32_t* meta = get_int32_data(ctx, argv[0], "meta");
  if (!meta) return JS_EXCEPTION;
  int32_t* elems = get_int32_data(ctx, argv[1], "elems");
  if (!elems) return JS_EXCEPTION;
  int32_t capacity = JS_VALUE_GET_INT(argv[2]);
  int32_t element = JS_VALUE_GET_INT(argv[3]);
  int32_t hash = JS_VALUE_GET_INT(argv[4]);
  int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

  int32_t probeMask = capacity;
  int32_t probeOffset = ((uint32_t)hash >> 7) & probeMask;
  int32_t probeIndex = 0;
  int32_t iter = 0;

  while (1) {
    iter++;
    if (iter > 100) {
      DBG_LOGE("  c_intset_find PROBE LOOP exceeded 100 iters (cap=%d hash2=%d probeOffset=%d)", capacity, hash2, probeOffset);
      return JS_NewInt32(ctx, -1);
    }
    uint64_t g = load_group(meta, probeOffset, capacity);
    uint64_t m = match_hash2(g, hash2);
    DBG_LOGI("  c_intset_find iter=%d probeOffset=%d probeIndex=%d g=0x%016llx m=0x%016llx",
             iter, probeOffset, probeIndex, (unsigned long long)g, (unsigned long long)m);
    while (m != 0) {
      int32_t bitIdx = __builtin_ctzll(m);
      int32_t byteInGroup = bitIdx >> 3;
      int32_t index = (probeOffset + byteInGroup) & probeMask;
      DBG_LOGI("    match byteInGroup=%d index=%d elems[%d]=%d (looking for %d)",
               byteInGroup, index, index, elems[index], element);
      if (elems[index] == element) {
        DBG_LOGI("  c_intset_find FOUND at index=%d", index);
        return JS_NewInt32(ctx, index);
      }
      m &= m - 1;
    }
    if (any_empty_or_deleted(g)) {
      DBG_LOGI("  c_intset_find group has empty/deleted, returning -1");
      break;
    }
    probeIndex += 8;
    probeOffset = (probeOffset + probeIndex) & probeMask;
  }
  return JS_NewInt32(ctx, -1);
}

static JSValue c_intset_add(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  DBG_LOGI("c_intset_add start");
  (void)this_val; (void)argc;
  int32_t* meta = get_int32_data(ctx, argv[0], "meta");
  if (!meta) return JS_EXCEPTION;
  int32_t* elems = get_int32_data(ctx, argv[1], "elems");
  if (!elems) return JS_EXCEPTION;
  int32_t* created = get_int32_data(ctx, argv[6], "created");
  if (!created) return JS_EXCEPTION;
  int32_t* sizeDelta = get_int32_data(ctx, argv[7], "sizeDelta");
  if (!sizeDelta) return JS_EXCEPTION;
  int32_t capacity = JS_VALUE_GET_INT(argv[2]);
  int32_t element = JS_VALUE_GET_INT(argv[3]);
  int32_t hash = JS_VALUE_GET_INT(argv[4]);
  int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

  int32_t probeMask = capacity;
  int32_t probeOffset = ((uint32_t)hash >> 7) & probeMask;
  int32_t probeIndex = 0;
  int32_t insertSlot = -1;

  while (1) {
    uint64_t g = load_group(meta, probeOffset, capacity);
    uint64_t m = match_hash2(g, hash2);
    while (m != 0) {
      int32_t bitIdx = __builtin_ctzll(m);
      int32_t byteInGroup = bitIdx >> 3;
      int32_t index = (probeOffset + byteInGroup) & probeMask;
      if (elems[index] == element) {
        created[0] = 0;
        sizeDelta[0] = 0;
        DBG_LOGI("c_intset_add end");
        return JS_NewInt32(ctx, index);
      }
      m &= m - 1;
    }
    int32_t slot = first_empty_or_deleted(g, probeOffset, capacity);
    if (slot >= 0) { insertSlot = slot; break; }
    probeIndex += 8;
    probeOffset = (probeOffset + probeIndex) & probeMask;
  }

  write_meta_byte(meta, insertSlot, hash2);
  elems[insertSlot] = element;
  created[0] = 1;
  sizeDelta[0] = 1;
  DBG_LOGI("c_intset_add end");
  return JS_NewInt32(ctx, insertSlot);
}

static JSValue c_intset_remove(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
DBG_LOGI("c_intset_remove start");
  (void)this_val; (void)argc;
  int32_t* meta = get_int32_data(ctx, argv[0], "meta");
  if (!meta) return JS_EXCEPTION;
  int32_t* elems = get_int32_data(ctx, argv[1], "elems");
  if (!elems) return JS_EXCEPTION;
  int32_t capacity = JS_VALUE_GET_INT(argv[2]);
  int32_t element = JS_VALUE_GET_INT(argv[3]);
  int32_t hash = JS_VALUE_GET_INT(argv[4]);
  int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

  int32_t probeMask = capacity;
  int32_t probeOffset = ((uint32_t)hash >> 7) & probeMask;
  int32_t probeIndex = 0;

  while (1) {
    uint64_t g = load_group(meta, probeOffset, capacity);
    uint64_t m = match_hash2(g, hash2);
    while (m != 0) {
      int32_t bitIdx = __builtin_ctzll(m);
      int32_t byteInGroup = bitIdx >> 3;
      int32_t index = (probeOffset + byteInGroup) & probeMask;
      if (elems[index] == element) {
        write_meta_byte(meta, index, META_DELETED);
DBG_LOGI("c_intset_remove end");
        return JS_NewInt32(ctx, index);
      }
      m &= m - 1;
    }
    if (any_empty(g)) break;
    probeIndex += 8;
    probeOffset = (probeOffset + probeIndex) & probeMask;
  }
DBG_LOGI("c_intset_remove end");
  return JS_NewInt32(ctx, -1);
}

// ============================================================================
// IntObjectMap intrinsics (same metadata layout as IntSet, but stores keys and values)
//
// Function signatures:
//   _intObjectMapFind(metadataFlat, keys, capacity, key, hash, hash2) -> index or -1
//   _intObjectMapPut(metadataFlat, keys, capacity, key, hash, hash2,
//                    outCreated, outSizeDelta) -> index (existing or new)
//   _intObjectMapRemove(metadataFlat, keys, capacity, key, hash, hash2) -> removed index or -1
// ============================================================================

static JSValue c_intobjectmap_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  DBG_LOGI("c_intobjectmap_find start");
  (void)this_val; (void)argc;
  int32_t* meta = get_int32_data(ctx, argv[0], "meta");
  if (!meta) return JS_EXCEPTION;
  int32_t* keys = get_int32_data(ctx, argv[1], "keys");
  if (!keys) return JS_EXCEPTION;
  int32_t capacity = JS_VALUE_GET_INT(argv[2]);
  int32_t key = JS_VALUE_GET_INT(argv[3]);
  int32_t hash = JS_VALUE_GET_INT(argv[4]);
  int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

  int32_t probeMask = capacity;           // FIX: use capacity (not capacity-1)
  int32_t probeOffset = ((uint32_t)hash >> 7) & probeMask;
  int32_t probeIndex = 0;
  int32_t scanned = 0;                    // track how many slots we've scanned

  while (1) {
    if (scanned > capacity) {             // safety: we've looked at all slots
      DBG_LOGE("c_intobjectmap_find: scanned all slots, key not found");
      break;
    }
    uint64_t g = load_group(meta, probeOffset, capacity);
    uint64_t m = match_hash2(g, hash2);
    while (m != 0) {
      int32_t bitIdx = __builtin_ctzll(m);
      int32_t byteInGroup = bitIdx >> 3;
      int32_t index = (probeOffset + byteInGroup) & (capacity - 1);
      if (keys[index] == key) {
        DBG_LOGI("c_intobjectmap_find end (found at %d)", index);
        return JS_NewInt32(ctx, index);
      }
      m &= m - 1;
    }
    if (any_empty(g)) break;
    probeIndex += 8;
    probeOffset = (probeOffset + probeIndex) & probeMask;
    scanned += 8;                         // we've scanned 8 new slots
  }
  DBG_LOGI("c_intobjectmap_find end (not found)");
  return JS_NewInt32(ctx, -1);
}

static JSValue c_intobjectmap_put(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
DBG_LOGI("c_intobjectmap_put start");
  (void)this_val; (void)argc;
  int32_t* meta = get_int32_data(ctx, argv[0], "meta");
  if (!meta) return JS_EXCEPTION;
  int32_t* keys = get_int32_data(ctx, argv[1], "keys");
  if (!keys) return JS_EXCEPTION;
  int32_t* created = get_int32_data(ctx, argv[6], "created");
  if (!created) return JS_EXCEPTION;
  int32_t* sizeDelta = get_int32_data(ctx, argv[7], "sizeDelta");
  if (!sizeDelta) return JS_EXCEPTION;
  int32_t capacity = JS_VALUE_GET_INT(argv[2]);
  int32_t key = JS_VALUE_GET_INT(argv[3]);
  int32_t hash = JS_VALUE_GET_INT(argv[4]);
  int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

  int32_t probeMask = capacity;
  int32_t probeOffset = ((uint32_t)hash >> 7) & probeMask;
  int32_t probeIndex = 0;
  int32_t insertSlot = -1;

  while (1) {
    uint64_t g = load_group(meta, probeOffset, capacity);
    uint64_t m = match_hash2(g, hash2);
    while (m != 0) {
      int32_t bitIdx = __builtin_ctzll(m);
      int32_t byteInGroup = bitIdx >> 3;
      int32_t index = (probeOffset + byteInGroup) & probeMask;
      if (keys[index] == key) {
        created[0] = 0;
        sizeDelta[0] = 0;
DBG_LOGI("c_intobjectmap_put end");
        return JS_NewInt32(ctx, index);
      }
      m &= m - 1;
    }
    int32_t slot = first_empty_or_deleted(g, probeOffset, capacity);
    if (slot >= 0) { insertSlot = slot; break; }
    probeIndex += 8;
    probeOffset = (probeOffset + probeIndex) & probeMask;
  }

  write_meta_byte(meta, insertSlot, hash2);
  keys[insertSlot] = key;
  created[0] = 1;
  sizeDelta[0] = 1;
DBG_LOGI("c_intobjectmap_put end");
  return JS_NewInt32(ctx, insertSlot);
}

static JSValue c_intobjectmap_remove(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
DBG_LOGI("c_intobjectmap_remove start");
  (void)this_val; (void)argc;
  int32_t* meta = get_int32_data(ctx, argv[0], "meta");
  if (!meta) return JS_EXCEPTION;
  int32_t* keys = get_int32_data(ctx, argv[1], "keys");
  if (!keys) return JS_EXCEPTION;
  int32_t capacity = JS_VALUE_GET_INT(argv[2]);
  int32_t key = JS_VALUE_GET_INT(argv[3]);
  int32_t hash = JS_VALUE_GET_INT(argv[4]);
  int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

  int32_t probeMask = capacity;
  int32_t probeOffset = ((uint32_t)hash >> 7) & probeMask;
  int32_t probeIndex = 0;

  while (1) {
    uint64_t g = load_group(meta, probeOffset, capacity);
    uint64_t m = match_hash2(g, hash2);
    while (m != 0) {
      int32_t bitIdx = __builtin_ctzll(m);
      int32_t byteInGroup = bitIdx >> 3;
      int32_t index = (probeOffset + byteInGroup) & probeMask;
      if (keys[index] == key) {
        write_meta_byte(meta, index, META_DELETED);
DBG_LOGI("c_intobjectmap_remove end");

        return JS_NewInt32(ctx, index);
      }
      m &= m - 1;
    }
    if (any_empty(g)) break;
    probeIndex += 8;
    probeOffset = (probeOffset + probeIndex) & probeMask;
  }
DBG_LOGI("c_intobjectmap_remove end");

  return JS_NewInt32(ctx, -1);
}

// Helper to allocate a flat metadata buffer from a LongArray-like representation.
// JS signature: _intsetMakeFlat(metadataLongArray) -> Int32Array
// Each Long in the input array becomes 8 consecutive bytes (little-endian).
static JSValue c_intset_make_flat(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
DBG_LOGI("c_intset_make_flat start");
  (void)this_val; (void)argc;
  // Get the source array's length and data
  JSValue srcLenVal = JS_GetPropertyStr(ctx, argv[0], "length");
  if (JS_IsException(srcLenVal)) return JS_EXCEPTION;
  int32_t srcLen = JS_VALUE_GET_INT(srcLenVal);
  JS_FreeValue(ctx, srcLenVal);
  int32_t byteCount = srcLen * 8;

  // Allocate a fresh Int32Array of that size
  JSValue int32Ctor = JS_GetPropertyStr(ctx, JS_GetGlobalObject(ctx), "Int32Array");
  JSValue arg = JS_NewInt32(ctx, byteCount);
  JSValue result = JS_CallConstructor(ctx, int32Ctor, 1, &arg);
  JS_FreeValue(ctx, int32Ctor);
  JS_FreeValue(ctx, arg);
  if (JS_IsException(result)) return JS_EXCEPTION;

  size_t s;
  int32_t* dst = (int32_t*)JS_GetArrayBuffer(ctx, &s, result);
  if (!dst) { JS_FreeValue(ctx, result); return JS_EXCEPTION; }

  // Iterate source Longs: for each Long, extract 8 bytes (little-endian) into dst[LongIdx*8..LongIdx*8+7].
  for (int32_t i = 0; i < srcLen; i++) {
    JSValue loVal = JS_GetPropertyStr(ctx, argv[0], "" /* not this */ );
    // The Kotlin/JS Long is an object {v4_1, w4_1}. We need indexed access.
    // Use JS_GetPropertyUint32 to read element i.
    JS_FreeValue(ctx, loVal);
    JSValue elem = JS_GetPropertyUint32(ctx, argv[0], i);
    if (JS_IsException(elem)) { JS_FreeValue(ctx, result); return JS_EXCEPTION; }
    JSValue lo = JS_GetPropertyStr(ctx, elem, "v4_1");
    JSValue hi = JS_GetPropertyStr(ctx, elem, "w4_1");
    int32_t loI = JS_VALUE_GET_INT(lo);
    int32_t hiI = JS_VALUE_GET_INT(hi);
    JS_FreeValue(ctx, elem);
    JS_FreeValue(ctx, lo);
    JS_FreeValue(ctx, hi);
    // bytes 0..3 = low 8 bits of loI, loI>>8, loI>>16, loI>>24
    dst[i*8 + 0] = loI & 0xFF;
    dst[i*8 + 1] = (loI >> 8) & 0xFF;
    dst[i*8 + 2] = (loI >> 16) & 0xFF;
    dst[i*8 + 3] = (loI >> 24) & 0xFF;
    // bytes 4..7 = hiI bytes
    dst[i*8 + 4] = hiI & 0xFF;
    dst[i*8 + 5] = (hiI >> 8) & 0xFF;
    dst[i*8 + 6] = (hiI >> 16) & 0xFF;
    dst[i*8 + 7] = (hiI >> 24) & 0xFF;
  }
DBG_LOGI("c_intset_make_flat end");
  return result;
}

// JS signature: _intObjectMapFindAvailableSlot(metadataFlat, capacity, hash1)
// Returns the index of the first Empty or Deleted slot starting from hash1.
static JSValue c_intobjectmap_find_available_slot(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)this_val; (void)argc;
  DBG_LOGI("c_intobjectmap_find_available_slot ENTER argc=%d", argc);
  for (int i = 0; i < argc; i++) {
    int t = JS_VALUE_GET_TAG(argv[i]);
    DBG_LOGI("  argv[%d] tag=%d", i, t);
  }
  int32_t capacity = JS_VALUE_GET_INT(argv[1]);
  int32_t hash1 = JS_VALUE_GET_INT(argv[2]);
  DBG_LOGI("  capacity=%d hash1=%d", capacity, hash1);

  int32_t* meta = get_int32_data(ctx, argv[0], "meta");
  if (!meta) {
    DBG_LOGE("c_intobjectmap_find_available_slot: get_int32_data returned NULL");
    return JS_EXCEPTION;
  }
  DBG_LOGI("  meta=%p capacity=%d", meta, capacity);

  int32_t probeMask = capacity;           // FIX: use capacity
  int32_t probeOffset = hash1 & probeMask;
  int32_t probeIndex = 0;
  int32_t scanned = 0;

  while (1) {
    if (scanned > capacity) {
      DBG_LOGE("c_intobjectmap_find_available_slot: scanned all slots, no Empty/Deleted found");
      break;
    }
    uint64_t g = load_group(meta, probeOffset, capacity);
    int32_t slot = first_empty_or_deleted(g, probeOffset, capacity);
    DBG_LOGI("  iter=%d probeOffset=%d probeIndex=%d group=0x%016llx slot=%d",
             scanned/8 + 1, probeOffset, probeIndex, (unsigned long long)g, slot);
    if (slot >= 0) {
      DBG_LOGI("c_intobjectmap_find_available_slot RETURN slot=%d", slot);
      return JS_NewInt32(ctx, slot);
    }
    probeIndex += 8;
    probeOffset = (probeOffset + probeIndex) & probeMask;
    scanned += 8;
  }
  DBG_LOGE("c_intobjectmap_find_available_slot: returning -1 (should not happen)");
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

static JSValue c_scatterset_find(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
  (void)this_val; (void)argc;
  int32_t* meta = get_int32_data(ctx, argv[0], "meta");
  if (!meta) return JS_EXCEPTION;
  // elements is a plain Array<Any?> (Object[] in JS), not a TypedArray. We can't
  // get_int32_data on it — we just access argv[1] directly via JS_GetPropertyUint32.
  int32_t capacity = JS_VALUE_GET_INT(argv[2]);
  JSValueConst element = argv[3];
  int32_t hash = JS_VALUE_GET_INT(argv[4]);
  int32_t hash2 = JS_VALUE_GET_INT(argv[5]);

  DBG_LOGI("c_scatterset_find ENTER cap=%d hash=%d hash2=%d", capacity, hash, hash2);

  int32_t probeMask = capacity;
  int32_t probeOffset = ((uint32_t)hash >> 7) & probeMask;
  int32_t probeIndex = 0;
  int32_t iter = 0;

  while (1) {
    iter++;
    if (iter > 100) {
      DBG_LOGE("c_scatterset_find PROBE LOOP exceeded 100 iters (cap=%d hash2=%d probeOffset=%d)", capacity, hash2, probeOffset);
      return JS_NewInt32(ctx, -1);
    }
    uint64_t g = load_group(meta, probeOffset, capacity);
    uint64_t m = match_hash2(g, hash2);
    DBG_LOGI("  iter=%d probeOffset=%d probeIndex=%d g=0x%016llx m=0x%016llx",
             iter, probeOffset, probeIndex, (unsigned long long)g, (unsigned long long)m);
    while (m != 0) {
      int32_t bitIdx = __builtin_ctzll(m);
      int32_t byteInGroup = bitIdx >> 3;
      int32_t index = (probeOffset + byteInGroup) & probeMask;
      // Read element[index] and compare to `element` using JS_StrictEq
      JSValue slotVal = JS_GetPropertyUint32(ctx, argv[1], (uint32_t)index);
      if (JS_IsException(slotVal)) {
        DBG_LOGE("c_scatterset_find: JS_GetPropertyUint32(%d) failed", index);
        return JS_EXCEPTION;
      }
      int eq = JS_StrictEq(ctx, slotVal, element);
      JS_FreeValue(ctx, slotVal);
      if (eq > 0) {
        DBG_LOGI("c_scatterset_find FOUND at index=%d", index);
        return JS_NewInt32(ctx, index);
      }
      m &= m - 1;
    }
    if (any_empty_or_deleted(g)) {
      DBG_LOGI("c_scatterset_find group has empty/deleted, returning -1");
      break;
    }
    probeIndex += 8;
    probeOffset = (probeOffset + probeIndex) & probeMask;
  }
  return JS_NewInt32(ctx, -1);
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
  fn = JS_NewCFunction(ctx, c_intset_find,                       "_intsetFind",                       6);
  JS_SetPropertyStr(ctx, globalThis, "_intsetFind", fn);
  fn = JS_NewCFunction(ctx, c_intset_add,                        "_intsetAdd",                        8);
  JS_SetPropertyStr(ctx, globalThis, "_intsetAdd", fn);
  fn = JS_NewCFunction(ctx, c_intset_remove,                     "_intsetRemove",                     6);
  JS_SetPropertyStr(ctx, globalThis, "_intsetRemove", fn);
  fn = JS_NewCFunction(ctx, c_intset_make_flat,                  "_intsetMakeFlat",                   1);
  JS_SetPropertyStr(ctx, globalThis, "_intsetMakeFlat", fn);
  fn = JS_NewCFunction(ctx, c_intobjectmap_find,                 "_intObjectMapFind",                 6);
  JS_SetPropertyStr(ctx, globalThis, "_intObjectMapFind", fn);
  fn = JS_NewCFunction(ctx, c_intobjectmap_put,                  "_intObjectMapPut",                  8);
  JS_SetPropertyStr(ctx, globalThis, "_intObjectMapPut", fn);
  fn = JS_NewCFunction(ctx, c_intobjectmap_remove,               "_intObjectMapRemove",               6);
  JS_SetPropertyStr(ctx, globalThis, "_intObjectMapRemove", fn);
  fn = JS_NewCFunction(ctx, c_intobjectmap_find_available_slot,  "_intObjectMapFindAvailableSlot",    3);
  JS_SetPropertyStr(ctx, globalThis, "_intObjectMapFindAvailableSlot", fn);
  fn = JS_NewCFunction(ctx, c_scatterset_find,                    "_scatterSetFind",                  6);
  JS_SetPropertyStr(ctx, globalThis, "_scatterSetFind", fn);
  // Generic debug log helper for Kotlin/JS code to log to logcat.
  fn = JS_NewCFunction(ctx, c_dbg_log,                           "_dbg",                              1);
  JS_SetPropertyStr(ctx, globalThis, "_dbg", fn);
  JS_FreeValue(ctx, globalThis);
}
