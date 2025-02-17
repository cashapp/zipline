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

import okio.ByteString.Companion.decodeHex
import org.junit.Test

/**
 *
 * cd zipline/src/jvmMain/build
 * cmake ..  && make
 *
 *
 */
class WasmRuntimeTest {
  private val wasmData = "0061736D0100000001100360017F017F60027F7F017F60017F0002310403656E760470757473000003656E76066D616C6C6F63000003656E76067072696E7466000103656E7604667265650002030201010405017001010105030100010613037F0141C0280B7F0041BA080B7F0041C0280B072C04066D656D6F727902000A5F5F646174615F656E6403010B5F5F686561705F626173650302046D61696E00040AB20101AF0101037F23808080800041206B2202248080808000419B888080001080808080001A0240024041800810818080800022030D0041A8888080001080808080001A417F21040C010B20022003360210418088808000200241106A1082808080001A41002104200341046A41002F0091888080003B00002003410028008D888080003600002002200336020041938880800020021082808080001A20031083808080000B200241206A24808080800020040B0B4101004180080B3A627566207074723A2025700A00313233340A006275663A2025730048656C6C6F20776F726C6421006D616C6C6F6320627566206661696C656400".decodeHex()

  @Test
  fun happyPath() {
    System.load("/Volumes/Development/zipline/zipline/src/jvmMain/build/libziplinewamr.dylib")
    println(WasmRuntime.init())
    val wasmModule = WasmModule.create(wasmData.toByteArray())
    println(wasmModule.wasmModule)

    println(WasmRuntime.test(wasmModule.wasmModule))

    wasmModule.close()
    println(WasmRuntime.destroy())
  }
}
