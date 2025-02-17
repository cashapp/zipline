/*
 * Copyright (C) 2025 Cash App
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
 */
#include <cstring>
#include <cstring>
#include <stdlib.h>
#include <jni.h>
#include <cinttypes>
#include <string>
#include <wasm_export.h>
#include "ZiplineWasmInternal.h"
#include "ZiplineWasmModule.h"

#include <cstdio>
#include <iostream>

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmRuntime_init(JNIEnv* env, jclass type)
{
    RuntimeInitArgs init_args;
    memset(&init_args, 0, sizeof(RuntimeInitArgs));

    init_args.mem_alloc_type = Alloc_With_Allocator;
    init_args.mem_alloc_option.allocator.malloc_func = (void *)malloc;
    init_args.mem_alloc_option.allocator.realloc_func = (void *)realloc;
    init_args.mem_alloc_option.allocator.free_func = (void *)free;

    /* initialize runtime environment */
    if (!wasm_runtime_full_init(&init_args)) {
        throwJavaException(env, "java/lang/IllegalStateException", "Init runtime failed");
        return;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_cash_zipline_WasmModule_load(JNIEnv* env, jclass type, jbyteArray byteCode)
{
    ZiplineWasmModule* result = new ZiplineWasmModule();
    if (!result->load(env, byteCode)) {
        delete result;
        return 0;
    }

    return reinterpret_cast<jlong>(result);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmModule_close(JNIEnv* env, jclass type, jlong _wasm_module)
{
    delete reinterpret_cast<ZiplineWasmModule*>(_wasm_module);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmModule_test(JNIEnv* env, jclass type, jlong _wasm_module)
{
    const auto wasm_module = reinterpret_cast<ZiplineWasmModule*>(_wasm_module);
    wasm_module->test(env);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmRuntime_destroy(JNIEnv* env, jclass type)
{
    wasm_runtime_destroy();
}
