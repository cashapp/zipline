/*
 * Copyright (C) 2023 Cash App
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
package app.cash.zipline.kotlin

import java.security.MessageDigest
import java.util.Base64

/** This would be more compact with Okio, but adding that dependency is tricky. */
fun String.signatureHash(): String {
  val signatureUtf8 = encodeToByteArray()
  val sha256 = MessageDigest.getInstance("SHA-256").digest(signatureUtf8)
  val sha256Prefix = sha256.sliceArray(0 until 6)
  return String(Base64.getEncoder().encode(sha256Prefix))
}
