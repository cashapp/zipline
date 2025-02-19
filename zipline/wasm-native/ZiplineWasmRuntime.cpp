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

    const auto wasmDataSize = env->GetArrayLength(wasmData);
    const auto wasmDataBuf = new jbyte[wasmDataSize];
    env->GetByteArrayRegion(wasmData, 0, wasmDataSize, wasmDataBuf);

    const auto wasmModule = wasm_runtime_load(reinterpret_cast<uint8_t*>(wasmDataBuf), wasmDataSize, errorBuf, sizeof(errorBuf));

    if (!wasmModule) {
        std::cout << "in wasm_runtime_load %s\n" << errorBuf << std::endl;
        throwJavaException(env, "java/lang/IllegalStateException", "Module load failed");
        return NULL;
    }

    const auto wasmExportClass = env->FindClass("app/cash/zipline/WasmExport");
    const auto wasmModuleClass = env->FindClass("app/cash/zipline/WasmModule");

    const auto exportCount = wasm_runtime_get_export_count(wasmModule);
    const auto exportsArray = env->NewObjectArray(exportCount, wasmExportClass, NULL);
    for (int i = 0; i < exportCount; i++) {
        env->SetObjectArrayElement(exportsArray, i, createExport(env, wasmModule, i));
    }

    const auto resultPointer = reinterpret_cast<jlong>(wasmModule);
    const auto wasmDataBufPointer = reinterpret_cast<jlong>(wasmDataBuf);
    const auto resultConstructor = env->GetMethodID(wasmModuleClass, "<init>", "(JJ[Lapp/cash/zipline/WasmExport;)V");

    return env->NewObject(wasmModuleClass, resultConstructor, resultPointer, wasmDataBufPointer, exportsArray);
}

jobject ZiplineWasmRuntime::createExport(JNIEnv* env, wasm_module_t wasmModule, int32_t index) {
    wasm_export_t exportType;
    wasm_runtime_get_export_type(wasmModule, index, &exportType);

    if (exportType.kind == WASM_IMPORT_EXPORT_KIND_FUNC) {
        const auto resultClass = env->FindClass("app/cash/zipline/WasmExport$Function");
        const auto resultConstructor = env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;)V");

        const auto name = env->NewStringUTF(exportType.name);
        return env->NewObject(resultClass, resultConstructor, name);
    }

    return NULL;
}
