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
package app.cash.zipline.api.validator.fir

import okio.ByteString.Companion.encodeUtf8

internal fun String.signatureHash(): String = encodeUtf8().sha256().substring(0, 6).base64() // In base64, 6 bytes takes 8 chars.

/** Don't bridge these. */
internal val NON_INTERFACE_FUNCTION_NAMES = setOf(
  "equals",
  "hashCode",
  "toString",
)
