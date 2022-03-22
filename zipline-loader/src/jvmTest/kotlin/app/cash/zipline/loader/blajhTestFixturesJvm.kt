/*
 * Copyright (C) 2022 Square, Inc.
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

import app.cash.zipline.QuickJs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class blajhTestFixturesJvm(quickJs: QuickJs) {
  val alphaJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "alpha loaded\n"
      |""".trimMargin()
  val alphaByteString = ziplineFile(quickJs, alphaJs, "alpha.js")
  val alphaSha256 = alphaByteString.sha256()
  val alphaSha256Hex = alphaSha256.hex()

  val bravoJs = """
      |globalThis.log = globalThis.log || "";
      |globalThis.log += "bravo loaded\n"
      |""".trimMargin()
  val bravoByteString = ziplineFile(quickJs, bravoJs, "bravo.js")
  val bravoSha256 = bravoByteString.sha256()
  val bravoSha256Hex = bravoSha256.hex()

  val manifest = ZiplineManifest.create(
    modules = mapOf(
      "bravo" to ZiplineModule(
        url = bravoFilePath,
        sha256 = bravoByteString.sha256(),
        dependsOnIds = listOf("alpha"),
      ),
      "alpha" to ZiplineModule(
        url = alphaFilePath,
        sha256 = alphaByteString.sha256(),
        dependsOnIds = listOf(),
      ),
    )
  )
  val manifestByteString = Json.encodeToString(manifest).encodeUtf8()

  private fun ziplineFile(quickJs: QuickJs, javaScript: String, fileName: String): ByteString {
    val ziplineFile = ZiplineFile(
      CURRENT_ZIPLINE_VERSION,
      quickJs.compile(javaScript, fileName).toByteString()
    )

    val buffer = Buffer()
    ziplineFile.writeTo(buffer)
    return buffer.readByteString()
  }

  companion object {
    const val alphaFilePath = "/alpha.zipline"
    const val bravoFilePath = "/bravo.zipline"
    const val manifestPath = "/manifest.zipline.json"
  }
}
