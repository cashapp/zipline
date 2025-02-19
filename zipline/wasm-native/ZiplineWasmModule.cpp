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
#include "ZiplineWasmModuleInstance.h"

ZiplineWasmModule::ZiplineWasmModule(wasm_module_t wasmModule)
    : wasmModule(wasmModule) {
}

jobject ZiplineWasmModule::createInstance(JNIEnv *env, jlong stackSize, jlong heapSize) {
    char error_buf[128] = { 0 };

    const auto result = wasm_runtime_instantiate(wasmModule, stackSize, heapSize, error_buf, sizeof(error_buf));
    if (!result) {
        std::cout << error_buf << std::endl;
        std::cout << "goto fail2" << std::endl;
        return NULL;
    }

    const auto resultPointer = reinterpret_cast<jlong>(result);
    return createJavaWrapper(env, "app/cash/zipline/WasmModuleInstance", resultPointer);
}
