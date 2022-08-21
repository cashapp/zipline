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

import kotlin.test.Test
import kotlin.test.assertEquals

/** Just confirm our [resolveUrl] code is wired up correctly. */
class ResolveUrlTest {
  @Test fun fullUrl() {
    assertEquals(
      "https://two.example.com/abc",
      resolveUrl("https://one.example.com/def", "https://two.example.com/abc"),
    )
  }

  @Test fun relativePath() {
    assertEquals(
      "https://example.com/abc",
      resolveUrl("https://example.com/", "abc"),
    )
  }

  @Test fun absolutePath() {
    assertEquals(
      "https://example.com/ghi",
      resolveUrl("https://example.com/abc/def", "/ghi"),
    )
  }
}
