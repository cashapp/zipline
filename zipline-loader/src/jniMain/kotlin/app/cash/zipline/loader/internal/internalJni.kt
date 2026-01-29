/*
 * Copyright (C) 2022 Block, Inc.
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
package app.cash.zipline.loader.internal

import app.cash.zipline.Zipline
import java.security.SecureRandom
import okhttp3.HttpUrl.Companion.toHttpUrl

internal actual fun Zipline.multiplatformLoadJsModule(bytecode: ByteArray, id: String) = loadJsModule(bytecode, id)

internal actual val ecdsaP256: SignatureAlgorithm = EcdsaP256(secureRandom())

internal fun secureRandom(): SecureRandom {
  return SecureRandom().also { it.nextLong() } // Force seeding.
}

internal actual val systemEpochMsClock: () -> Long = System::currentTimeMillis

internal actual fun resolveUrl(baseUrl: String, link: String): String {
  return baseUrl.toHttpUrl().resolve(link)!!.toString()
}
