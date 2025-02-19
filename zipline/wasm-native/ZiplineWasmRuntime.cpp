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

ZiplineWasmRuntime::ZiplineWasmRuntime() {
}

ZiplineWasmRuntime::~ZiplineWasmRuntime() {
    wasm_runtime_destroy();
}

jobject ZiplineWasmRuntime::createModule(JNIEnv* env, jbyteArray wasmData) {
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

    const auto resultPointer = reinterpret_cast<jlong>(wasmModule);
    return createJavaWrapper(env, "app/cash/zipline/WasmModule", resultPointer);
}
