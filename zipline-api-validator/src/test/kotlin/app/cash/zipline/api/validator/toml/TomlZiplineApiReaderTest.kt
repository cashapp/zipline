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

internal class TomlZiplineApiReaderTest {
  @Test
  fun happyPath() {
    val toml = Buffer().writeUtf8(
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
      """.trimMargin(),
    )

    val ziplineApi = toml.readTomlZiplineApi()
    assertThat(ziplineApi).isEqualTo(
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

  @Test
  fun multipleServices() {
    val toml = Buffer().writeUtf8(
      """
      |[com.example.Service1]
      |functions = [ "abc21acx" ]
      |[com.example.Service2]
      |functions = [ "1acbabc2" ]
      """.trimMargin(),
    )

    val ziplineApi = toml.readTomlZiplineApi()
    assertThat(ziplineApi).isEqualTo(
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

  @Test
  fun ignoredComments() {
    val toml = Buffer().writeUtf8(
      """
      |# comment
      |#comment without leading whitespace
      |# another comment
      |
      |# comment after blank line
      |# comment preceding section
      |[com.example.SampleService] # comment on the same line as the section
      |# comment after section
      |functions = [ # comment after functions list
      |  # function before function ID
      |  "abc21acx", # comment after function ID
      |  "1acbabc2",
      |# comment after the last function ID
      |] # comment at the end of the file
      """.trimMargin(),
    )

    val ziplineApi = toml.readTomlZiplineApi()
    assertThat(ziplineApi).isEqualTo(
      TomlZiplineApi(
        services = listOf(
          TomlZiplineService(
            name = "com.example.SampleService",
            functions = listOf(
              TomlZiplineFunction(
                leadingComment = "comment after functions list\nfunction before function ID",
                id = "abc21acx",
              ),
              TomlZiplineFunction(
                leadingComment = "comment after function ID",
                id = "1acbabc2",
              ),
            ),
          ),
        ),
      ),
    )
  }
}
