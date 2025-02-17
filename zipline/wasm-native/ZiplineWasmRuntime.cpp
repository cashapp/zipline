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
Java_app_cash_zipline_WasmModule_load(JNIEnv* env, jclass type, jbyteArray wasmData)
{
    char error_buf[128] = { 0 };

    const auto wasm_file_buf = env->GetByteArrayElements(wasmData, NULL);
    const auto wasm_file_size = env->GetArrayLength(wasmData);
    const auto wasm_module = wasm_runtime_load(reinterpret_cast<uint8_t*>(wasm_file_buf), wasm_file_size, error_buf, sizeof(error_buf));
    env->ReleaseByteArrayElements(wasmData, wasm_file_buf, JNI_ABORT);

    if (!wasm_module) {
        std::cout << "in wasm_runtime_load %s\n" << error_buf << std::endl;
        throwJavaException(env, "java/lang/IllegalStateException", "Module load failed");
        return NULL;
    }

    const auto result = new ZiplineWasmModule(wasm_module);
    return reinterpret_cast<jlong>(result);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmModule_close(JNIEnv* env, jclass type, jlong _wasm_module)
{
    delete reinterpret_cast<ZiplineWasmModule*>(_wasm_module);
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_cash_zipline_WasmModule_createInstance(JNIEnv* env, jclass type, jlong _wasm_module)
{
    const auto module = reinterpret_cast<ZiplineWasmModule*>(_wasm_module);
    const auto moduleInstance = module->createInstance(env);
    return reinterpret_cast<jlong>(moduleInstance);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmModuleInstance_main(JNIEnv* env, jclass type, jlong _wasm_module_instance)
{
    const auto  moduleInstance = reinterpret_cast<ZiplineWasmModuleInstance*>(_wasm_module_instance);
    moduleInstance->main(env);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmModuleInstance_close(JNIEnv* env, jclass type, jlong _wasm_module_instance)
{
    delete reinterpret_cast<ZiplineWasmModuleInstance*>(_wasm_module_instance);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmRuntime_destroy(JNIEnv* env, jclass type)
{
    wasm_runtime_destroy();
}
