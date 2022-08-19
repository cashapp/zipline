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

import app.cash.zipline.loader.testing.SampleKeys
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okio.ByteString.Companion.encodeUtf8

/**
 * Just confirm our ECDSA code is wired up correctly. We're using the platform's implementation and
 * assume it's already tested properly.
 */
class EcdsaTest {
  @Test
  fun happyPath() {
    val data = "hello world".encodeUtf8()
    val signature = ecdsa.sign(SampleKeys.key4Private, data)

    // Valid signature verifies.
    ecdsa.verify(SampleKeys.key4Public, signature, data)

    // If the data changes, it doesn't verify.
    assertFailsWith<IllegalStateException> {
      ecdsa.verify(SampleKeys.key4Public, signature, "hello World".encodeUtf8())
    }

    // If the key changes, it doesn't verify.
    assertFailsWith<IllegalStateException> {
      ecdsa.verify(SampleKeys.key5Public, signature, data)
    }
  }
}

