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
#include "ZiplineWasmModuleInstance.h"
#include <iostream>
#include <jni.h>
#include <wasm_export.h>

ZiplineWasmModuleInstance::ZiplineWasmModuleInstance(wasm_module_inst_t wasm_module_instance)
    : wasm_module_instance(wasm_module_instance) {
}

ZiplineWasmModuleInstance::~ZiplineWasmModuleInstance() {
    std::cout << "wasm_runtime_deinstantiate" << std::endl;
    wasm_runtime_deinstantiate(wasm_module_instance);
}

void ZiplineWasmModuleInstance::main(JNIEnv* env) {
    std::cout << "run main() of the application" << std::endl;

    wasm_application_execute_main(wasm_module_instance, 0, NULL);

    const char *exception;
    if ((exception = wasm_runtime_get_exception(wasm_module_instance))) {
        std::cout << exception << std::endl;
    }
}
