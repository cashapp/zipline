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
#include <vector>
#include "ZiplineWasmFunction.h"
#include "ZiplineWasmModule.h"
#include "ZiplineWasmModuleInstance.h"

ZiplineWasmModuleInstance::ZiplineWasmModuleInstance(wasm_module_inst_t wasmModuleInstance)
    : wasmModuleInstance(wasmModuleInstance) {
}

jobject ZiplineWasmModuleInstance::function(JNIEnv* env, jstring name) {
    const auto nameCstr = env->GetStringUTFChars(name, 0);

    const auto result = wasm_runtime_lookup_function(wasmModuleInstance, nameCstr);
    env->ReleaseStringUTFChars(name, nameCstr);
    if (!result) {
        return NULL;
    }

    const auto wasmFunctionClass = env->FindClass("app/cash/zipline/WasmFunction");
    const auto wasmValueTypeClass = env->FindClass("app/cash/zipline/WasmValueType");

    const auto paramCount = wasm_func_get_param_count(result, wasmModuleInstance);
    std::vector<wasm_valkind_t> paramTypes(paramCount);
    wasm_func_get_param_types(result, wasmModuleInstance, paramTypes.data());
    const auto paramTypesArray = env->NewObjectArray(paramCount, wasmValueTypeClass, NULL);

    const auto resultCount = wasm_func_get_result_count(result, wasmModuleInstance);
    std::vector<wasm_valkind_t> resultTypes(resultCount);
    wasm_func_get_result_types(result, wasmModuleInstance, resultTypes.data());
    const auto resultTypesArray = env->NewObjectArray(resultCount, wasmValueTypeClass, NULL);

    const auto wasmFunction = new ZiplineWasmFunction(result);
    const auto resultPointer = reinterpret_cast<jlong>(wasmFunction);
    const auto resultConstructor = env->GetMethodID(wasmFunctionClass, "<init>", "(J[Lapp/cash/zipline/WasmValueType;[Lapp/cash/zipline/WasmValueType;)V");

    return env->NewObject(wasmFunctionClass, resultConstructor, resultPointer, paramTypesArray, resultTypesArray);
}
