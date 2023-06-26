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
package app.cash.zipline.api.validator.toml

import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.Buffer
import org.junit.Test

internal class TomlZiplineApiWriterTest {
  @Test
  fun happyPath() {
    val buffer = Buffer().apply {
      writeTomlZiplineApi(
        TomlZiplineApi(
          services = listOf(
            TomlZiplineService(
              name = "com.example.SampleService",
              functions = listOf(
                TomlZiplineFunction(
                  leadingComment = "val name: kotlin.String",
                  id = "abc21acx",
                ),
                TomlZiplineFunction(
                  leadingComment = "fun echo(kotlin.String): kotlin.String",
                  id = "1acbabc2",
                ),
              ),
            ),
          ),
        ),
      )
    }

    assertThat(buffer.readUtf8()).isEqualTo(
      """
      |[com.example.SampleService]
      |
      |functions = [
      |  # val name: kotlin.String
      |  "abc21acx",
      |
      |  # fun echo(kotlin.String): kotlin.String
      |  "1acbabc2",
      |]
      |
      """.trimMargin(),
    )
  }

  @Test
  fun multipleServices() {
    val buffer = Buffer().apply {
      writeTomlZiplineApi(
        TomlZiplineApi(
          services = listOf(
            TomlZiplineService(
              name = "com.example.Service1",
              functions = listOf(TomlZiplineFunction("", "abc21acx")),
            ),
            TomlZiplineService(
              name = "com.example.Service2",
              functions = listOf(TomlZiplineFunction("", "1acbabc2")),
            ),
          ),
        ),
      )
    }

    assertThat(buffer.readUtf8()).isEqualTo(
      """
      |[com.example.Service1]
      |
      |functions = [
      |  "abc21acx",
      |]
      |
      |[com.example.Service2]
      |
      |functions = [
      |  "1acbabc2",
      |]
      |
      """.trimMargin(),
    )
  }
}
