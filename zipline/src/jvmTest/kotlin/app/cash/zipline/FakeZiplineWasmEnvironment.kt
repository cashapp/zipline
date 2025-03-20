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
package app.cash.zipline

import app.cash.zipline.chasm.ZiplineWasmEnvironment
import app.cash.zipline.chasm.i32
import io.github.charlietap.chasm.runtime.value.NumberValue
import okio.Buffer
import okio.ByteString

class FakeZiplineWasmEnvironment : ZiplineWasmEnvironment() {
  val stdout = Buffer()

  override fun randomGet(a: NumberValue.I32, b: NumberValue.I32): NumberValue.I32 {
    return 5.i32
  }

  override fun fdWrite(fileDescriptor: NumberValue.I32, data: ByteString) {
    println(data.utf8())
    stdout.write(data)
  }
}
