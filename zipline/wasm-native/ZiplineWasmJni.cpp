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
#include <iostream>
#include <jni.h>
#include <wasm_export.h>
#include "ZiplineWasmInternal.h"
#include "ZiplineWasmModule.h"
#include "ZiplineWasmRuntime.h"

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_zipline_WasmRuntime_createJni(JNIEnv* env, jclass type)
{
    RuntimeInitArgs initArgs;
    memset(&initArgs, 0, sizeof(RuntimeInitArgs));

    initArgs.mem_alloc_type = Alloc_With_Allocator;
    initArgs.mem_alloc_option.allocator.malloc_func = (void *)malloc;
    initArgs.mem_alloc_option.allocator.realloc_func = (void *)realloc;
    initArgs.mem_alloc_option.allocator.free_func = (void *)free;

    if (!wasm_runtime_full_init(&initArgs)) {
        throwJavaException(env, "java/lang/IllegalStateException", "Init runtime failed");
        return NULL;
    }

    const auto result = new ZiplineWasmRuntime();
    const auto resultPointer = reinterpret_cast<jlong>(result);
    return createJavaWrapper(env, "app/cash/zipline/WasmRuntime", resultPointer);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmRuntime_close(JNIEnv* env, jobject receiver)
{
    const auto receiverPointer = setPointerField(env, receiver, 0);
    if (receiverPointer != 0) {
      delete reinterpret_cast<ZiplineWasmRuntime*>(receiverPointer);
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_zipline_WasmRuntime_createModule(JNIEnv* env, jobject receiver, jbyteArray wasmData)
{
    const auto receiverPointer = getPointerField(env, receiver);
    const auto runtime = reinterpret_cast<ZiplineWasmRuntime*>(receiverPointer);
    return runtime->createModule(env, wasmData);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmModule_close(JNIEnv* env, jobject receiver)
{
    const auto receiverPointer = setPointerField(env, receiver, 0);
    if (receiverPointer != 0) {
        const auto module = reinterpret_cast<wasm_module_t>(receiverPointer);
        ZiplineWasmModule(module).close(env, receiver);
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_zipline_WasmModule_createInstance(JNIEnv* env, jobject receiver, jlong stackSize, jlong heapSize)
{
    const auto receiverPointer = getPointerField(env, receiver);
    const auto module = reinterpret_cast<wasm_module_t>(receiverPointer);
    return ZiplineWasmModule(module).createInstance(env, stackSize, heapSize);
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_zipline_WasmModuleInstance_function(JNIEnv* env, jobject receiver, jstring name)
{
    const auto receiverPointer = getPointerField(env, receiver);
    const auto moduleInstance = reinterpret_cast<wasm_module_inst_t>(receiverPointer);
    return ZiplineWasmModuleInstance(moduleInstance).function(env, name);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmModuleInstance_close(JNIEnv* env, jobject receiver)
{
    const auto receiverPointer = setPointerField(env, receiver, 0);
    if (receiverPointer != 0) {
      wasm_runtime_deinstantiate(reinterpret_cast<wasm_module_inst_t>(receiverPointer));
    }
}
