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
package app.cash.zipline.loader

import app.cash.zipline.loader.internal.cache.NativeSqliteDriverFactory
import app.cash.zipline.loader.internal.cache.SqlDriverFactory
import kotlin.random.Random
import okio.ByteString
import okio.ByteString.Companion.toByteString

internal actual fun testSqlDriverFactory(): SqlDriverFactory = NativeSqliteDriverFactory()

actual fun randomByteString(size: Int): ByteString {
  return Random.nextBytes(size).toByteString()
}

internal actual fun canSignEcdsaP256() = false
