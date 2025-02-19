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
#ifndef ZIPLINE_WASM_RUNTIME_H
#define ZIPLINE_WASM_RUNTIME_H

#include <jni.h>
#include <wasm_export.h>
#include "ZiplineWasmInternal.h"
#include "ZiplineWasmModule.h"

class ZiplineWasmRuntime {
public:
    ZiplineWasmRuntime();
    ~ZiplineWasmRuntime();

    jobject createModule(JNIEnv* env, jbyteArray wasmData);
    jobject createExport(JNIEnv* env, wasm_module_t wasmModule, int32_t index);
};

#endif //ZIPLINE_WASM_RUNTIME_H
