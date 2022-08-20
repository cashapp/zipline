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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8

/**
 * Just confirm our ECDSA code is wired up correctly. We're using the platform's implementation and
 * assume it's already tested properly.
 *
 * There are more tests for this class in `EcdsaP256JniTest`.
 */
class EcdsaP256Test {
  @Test
  fun verifyGoldenValues() {
    val data = "hello world".encodeUtf8()
    assertTrue(ecdsaP256.verify(data, SampleKeys.key4HelloWorldsignatureA, SampleKeys.key4Public))
    assertTrue(ecdsaP256.verify(data, SampleKeys.key4HelloWorldsignatureB, SampleKeys.key4Public))

    // If the data changes, it doesn't verify.
    assertFalse(ecdsaP256.verify(
      message = "hello World".encodeUtf8(),
      signature = SampleKeys.key4HelloWorldsignatureA,
      publicKey = SampleKeys.key4Public
    ))

    // If the key changes, it doesn't verify.
    assertFalse(ecdsaP256.verify(
      message = data,
      signature = SampleKeys.key4HelloWorldsignatureA,
      publicKey = SampleKeys.key5Public
    ))
  }
}

