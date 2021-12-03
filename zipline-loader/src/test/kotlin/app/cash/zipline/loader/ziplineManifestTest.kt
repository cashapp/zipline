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

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import org.junit.Test

// for some reason naming this file ZiplineManifestTest.kt breaks this file in Intellij
class ZiplineManifestTest {
  @Test
  fun `manifest sorts modules on create`() {
    val unsorted = ZiplineManifest.create(
      modules = mapOf(
        "bravo" to ZiplineModule(
          url = "/bravo.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf("alpha"),
        ),
        "alpha" to ZiplineModule(
          url = "/alpha.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf(),
        ),
      )
    )
    val sorted = ZiplineManifest.create(
      modules = mapOf(
        "alpha" to ZiplineModule(
          url = "/alpha.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf(),
        ),
        "bravo" to ZiplineModule(
          url = "/bravo.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf("alpha"),
        ),
      )
    )
    assertThat(unsorted).isEqualTo(sorted)
  }

  @Test
  fun `manifest checks that modules are sorted if class is copied`() {
    val empty = ZiplineManifest.create(
      modules = mapOf()
    )
    val unsortedException = assertFailsWith<IllegalArgumentException> {
      empty.copy(
        modules = mapOf(
          "bravo" to ZiplineModule(
            url = "/bravo.zipline",
            sha256 = "abc123".encodeUtf8(),
            dependsOnIds = listOf("alpha"),
          ),
          "alpha" to ZiplineModule(
            url = "/alpha.zipline",
            sha256 = "abc123".encodeUtf8(),
            dependsOnIds = listOf(),
          ),

          )
      )
    }
    assertThat(unsortedException.message).isEqualTo("Modules are not topologically sorted and can not be loaded")
  }

  @Test
  fun `fails on create when cyclical dependencies`() {
    val selfDependencyException = assertFailsWith<IllegalArgumentException> {
      ZiplineManifest.create(
        modules = mapOf(
          "alpha" to ZiplineModule(
            url = "/alpha.zipline",
            sha256 = "abc123".encodeUtf8(),
            dependsOnIds = listOf("alpha"),
          ),
        )
      )
    }
    assertThat(selfDependencyException.message).isEqualTo("No topological ordering is possible for [alpha]")

    val cyclicalException = assertFailsWith<IllegalArgumentException> {
      ZiplineManifest.create(
        modules = mapOf(
          "alpha" to ZiplineModule(
            url = "/alpha.zipline",
            sha256 = "abc123".encodeUtf8(),
            dependsOnIds = listOf("bravo"),
          ),
          "bravo" to ZiplineModule(
            url = "/bravo.zipline",
            sha256 = "abc123".encodeUtf8(),
            dependsOnIds = listOf("alpha"),
          ),
        )
      )
    }
    assertThat(cyclicalException.message).isEqualTo("No topological ordering is possible for [alpha, bravo]")
  }

  @Test
  fun `serializes to json`() {
    val original = ZiplineManifest.create(
      modules = mapOf(
        "alpha" to ZiplineModule(
          url = "/alpha.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf(),
        ),
        "bravo" to ZiplineModule(
          url = "/bravo.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf("alpha"),
        ),
      )
    )

    val serialized = Json { prettyPrint = true }.encodeToString(original)
    assertThat(serialized).isEqualTo(
      """
      |{
      |    "modules": {
      |        "alpha": {
      |            "url": "/alpha.zipline",
      |            "sha256": "616263313233"
      |        },
      |        "bravo": {
      |            "url": "/bravo.zipline",
      |            "sha256": "616263313233",
      |            "dependsOnIds": [
      |                "alpha"
      |            ]
      |        }
      |    }
      |}
    """.trimMargin()
    )

    val parsed = Json.decodeFromString<ZiplineManifest>(serialized)
    assertThat(parsed).isEqualTo(original)
  }
}
