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
#include <jni.h>
#include <wasm_export.h>
#include "ZiplineWasmInternal.h"
#include "ZiplineWasmModule.h"
#include "ZiplineWasmRuntime.h"
#include <iostream>

jlong getPointerField(JNIEnv* env, jobject receiver) {
    // TODO: queue an IllegalStateException('closed') if the result is 0.
    const auto receiverClass = env->GetObjectClass(receiver);
    const auto pointerField = env->GetFieldID(receiverClass, "pointer", "J");
    return env->GetLongField(receiver, pointerField);
}

jlong setPointerField(JNIEnv* env, jobject receiver, jlong newValue) {
    const auto receiverClass = env->GetObjectClass(receiver);
    const auto pointerField = env->GetFieldID(receiverClass, "pointer", "J");
    const auto oldValue = env->GetLongField(receiver, pointerField);
    env->SetLongField(receiver, pointerField, newValue);
    return oldValue;
}

jobject createJavaWrapper(JNIEnv* env, const char* className, jlong pointer) {
    const auto resultClass = env->FindClass(className);
    const auto resultConstructor = env->GetMethodID(resultClass, "<init>", "(J)V");
    return env->NewObject(resultClass, resultConstructor, pointer);
}

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

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_zipline_WasmRuntime_createModule(JNIEnv* env, jobject receiver, jbyteArray wasmData)
{
    char errorBuf[128] = { 0 };

    const auto wasmDataBuf = env->GetByteArrayElements(wasmData, NULL);
    const auto wasmDataSize = env->GetArrayLength(wasmData);
    const auto wasmModule = wasm_runtime_load(reinterpret_cast<uint8_t*>(wasmDataBuf), wasmDataSize, errorBuf, sizeof(errorBuf));
    env->ReleaseByteArrayElements(wasmData, wasmDataBuf, JNI_ABORT);

    if (!wasmModule) {
        std::cout << "in wasm_runtime_load %s\n" << errorBuf << std::endl;
        throwJavaException(env, "java/lang/IllegalStateException", "Module load failed");
        return NULL;
    }

    const auto result = new ZiplineWasmModule(wasmModule);

    const auto resultPointer = reinterpret_cast<jlong>(result);
    return createJavaWrapper(env, "app/cash/zipline/WasmModule", resultPointer);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmModule_close(JNIEnv* env, jobject receiver)
{
    const auto receiverPointer = setPointerField(env, receiver, 0);
    if (receiverPointer != 0) {
      delete reinterpret_cast<ZiplineWasmModule*>(receiverPointer);
    }
}

extern "C" JNIEXPORT jobject JNICALL
Java_app_cash_zipline_WasmModule_createInstanceJni(JNIEnv* env, jobject receiver, jlong stackSize, jlong heapSize)
{
    const auto receiverPointer = getPointerField(env, receiver);
    const auto module = reinterpret_cast<ZiplineWasmModule*>(receiverPointer);
    const auto result = module->createInstance(env, stackSize, heapSize);
    const auto resultPointer = reinterpret_cast<jlong>(result);
    return createJavaWrapper(env, "app/cash/zipline/WasmModuleInstance", resultPointer);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmModuleInstance_main(JNIEnv* env, jobject receiver)
{
    const auto receiverPointer = getPointerField(env, receiver);
    const auto moduleInstance = reinterpret_cast<ZiplineWasmModuleInstance*>(receiverPointer);
    moduleInstance->main(env);
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmModuleInstance_close(JNIEnv* env, jobject receiver)
{
    const auto receiverPointer = setPointerField(env, receiver, 0);
    if (receiverPointer != 0) {
      delete reinterpret_cast<ZiplineWasmModuleInstance*>(receiverPointer);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_app_cash_zipline_WasmRuntime_close(JNIEnv* env, jobject receiver)
{
    const auto receiverPointer = setPointerField(env, receiver, 0);
    if (receiverPointer != 0) {
      delete reinterpret_cast<ZiplineWasmRuntime*>(receiverPointer);
    }
}
