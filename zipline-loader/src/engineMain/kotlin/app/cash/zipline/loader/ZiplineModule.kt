/*
 * Copyright (C) 2021 Square, Inc.
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

import kotlinx.serialization.Serializable
import okio.ByteString

@Serializable
data class ZiplineModule(
  /** This may be an absolute URL, or relative to an enclosing manifest. */
  val url: String,
  @Serializable(with = ByteStringAsHexSerializer::class)
  val sha256: ByteString,
  val dependsOnIds: List<String> = listOf(),
  val patchFrom: String? = null,
  val patchUrl: String? = null,
)
