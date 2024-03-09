/*
 * Copyright (C) 2024 Cash App
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
package app.cash.zipline.cryptography

internal class BridgedSecureRandom internal constructor(
  private val securityService: ZiplineCryptographyService,
) : SecureRandom {
  override fun nextBytes(sink: ByteArray, offset: Int, count: Int) {
    require(offset + count <= sink.size) {
      "offset: $offset + count: $count > sink.size: ${sink.size}"
    }
    val byteArray = securityService.nextSecureRandomBytes(sink.size)
    byteArray.copyInto(sink, offset, 0, count)
  }

  override fun nextLong(): Long {
    val data = securityService.nextSecureRandomBytes(8)
    return (
      data[0].toLong() and 0xffL shl 56
        or (data[1].toLong() and 0xffL shl 48)
        or (data[2].toLong() and 0xffL shl 40)
        or (data[3].toLong() and 0xffL shl 32)
        or (data[4].toLong() and 0xffL shl 24)
        or (data[5].toLong() and 0xffL shl 16)
        or (data[6].toLong() and 0xffL shl 8)
        or (data[7].toLong() and 0xffL)
    )
  }
}
