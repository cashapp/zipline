/*
 * Copyright (C) 2021 Square, Inc.
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
package app.cash.zipline

import java.io.BufferedReader
import kotlin.reflect.KClass

/** Load our testing libraries into QuickJS. */
fun Zipline.loadTestingJs() {
  // Load modules in topologically-sorted order. In production the zipline-manifest does this.
  loadJsModuleFromResource("./kotlin_kotlin.js")
  loadJsModuleFromResource("./kotlin_org_jetbrains_kotlinx_atomicfu.js")
  loadJsModuleFromResource("./kotlin_org_jetbrains_kotlinx_kotlinx_serialization_core.js")
  loadJsModuleFromResource("./kotlin_org_jetbrains_kotlinx_kotlinx_serialization_json.js")
  loadJsModuleFromResource("./kotlin_org_jetbrains_kotlinx_kotlinx_coroutines_core.js")
  loadJsModuleFromResource("./kotlin_app_cash_zipline_zipline.js")
  loadJsModuleFromResource("./zipline-root-testing.js")
  quickJs.evaluate("globalThis['testing'] = require('./zipline-root-testing.js');")
}

private fun Zipline.loadJsModuleFromResource(fileName: String) {
  val fileJs = Zipline::class.java.getResourceAsStream(fileName.removePrefix("."))!!
    .bufferedReader()
    .use(BufferedReader::readText)
  loadJsModule(fileJs, fileName)
}

/**
 * See FinalizationTester for discussion on how to best trigger GC in tests.
 * https://android.googlesource.com/platform/libcore/+/master/support/src/test/java/libcore/
 * java/lang/ref/FinalizationTester.java
 */
fun awaitGarbageCollection() {
  Runtime.getRuntime().gc()
  Thread.sleep(100)
  System.runFinalization()
}

operator fun <T : Any> QuickJs.set(name: String, type: KClass<T>, instance: T) {
  error("no longer supported")
}

operator fun <T : Any> QuickJs.get(name: String, type: KClass<T>): T {
  error("no longer supported")
}
