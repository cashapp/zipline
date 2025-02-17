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
#include "ZiplineWasmModule.h"
#include <iostream>
#include <jni.h>
#include <wasm_export.h>

ZiplineWasmModule::ZiplineWasmModule() {
}

ZiplineWasmModule::~ZiplineWasmModule() {
    wasm_runtime_unload(wasm_module);
}

bool ZiplineWasmModule::load(JNIEnv *env, jbyteArray wasmData) {
    char error_buf[128] = { 0 };

    const auto wasm_file_buf = env->GetByteArrayElements(wasmData, NULL);
    const auto wasm_file_size = env->GetArrayLength(wasmData);
    wasm_module = wasm_runtime_load(reinterpret_cast<uint8_t*>(wasm_file_buf), wasm_file_size, error_buf, sizeof(error_buf));
    env->ReleaseByteArrayElements(wasmData, wasm_file_buf, JNI_ABORT);

    if (!wasm_module) {
        std::cout << "in wasm_runtime_load %s\n" << error_buf << std::endl;
        throwJavaException(env, "java/lang/IllegalStateException", "Module load failed");
        return false;
    }

    return true;
}
