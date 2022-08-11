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

import app.cash.zipline.EventListener
import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem
import java.security.SecureRandom
import okio.ByteString
import okio.ByteString.Companion.toByteString
import app.cash.zipline.loader.internal.cache.SqlDriverFactory

// This file is necessary to get Android tests to build.
//
// Note that we don't run Android tests because we don't have the right QuickJS or SQLite to run
// them on the JVM.

actual val systemFileSystem = FileSystem.SYSTEM

actual fun testZiplineLoader(
  dispatcher: CoroutineDispatcher,
  httpClient: ZiplineHttpClient,
  nowEpochMs: () -> Long,
  eventListener: EventListener,
  manifestVerifier: ManifestVerifier?,
): ZiplineLoader = error("testZiplineLoader not available for Android")

internal actual fun testSqlDriverFactory(): SqlDriverFactory =
  error("testSqlDriverFactory not available for Android")

actual fun randomByteString(size: Int): ByteString {
  val byteArray = ByteArray(size)
  testSecureRandom().nextBytes(byteArray)
  return byteArray.toByteString()
}

private fun testSecureRandom() = SecureRandom()
  .also {
    it.nextLong() // Force seeding.
  }

internal actual fun canLoadTestResources() = true
