/*
 * Copyright (C) 2024 Cash App
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
package app.cash.zipline.bytecode

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

internal class SourcePathCleanerTest {
  private val cleaner = SourcePathCleaner()

  /** Real paths collected from production applications. */
  @Test
  fun happyPath() {
    assertCleaned(
      "../../../../../../../../../../../../../../../mnt/agent/work/88b0986a7186d029/atomicfu/src/jsAndWasmSharedMain/kotlin/kotlinx/atomicfu/AtomicFU.kt",
      "kotlinx/atomicfu/AtomicFU.kt",
    )
    assertCleaned(
      "../../../../../../../../../../../../../../../Users/jack/workspace/wire/wire-runtime/src/jsMain/kotlin/com/squareup/wire/Message.kt",
      "com/squareup/wire/Message.kt",
    )
    assertCleaned(
      "js-ir/src/kotlin/coroutines_13/CoroutineImpl.kt",
      "coroutines_13/CoroutineImpl.kt",
    )
    assertCleaned(
      "../../../../../../../../arcade/compose-protocol/build/generated/redwood/sources/app/cash/arcade/protocol/guest/redwoodlayout/ProtocolColumn.kt",
      "app/cash/arcade/protocol/guest/redwoodlayout/ProtocolColumn.kt",
    )
    assertCleaned(
      "../../../../../../../../../../../../../../../mnt/agent/work/44ec6e850d5c63f0/kotlinx-coroutines-core/common/src/Job.kt",
      "Job.kt",
    )
    assertCleaned(
      "../../../../../../../../../../../../../../../Volumes/Development/zipline/zipline/src/commonMain/kotlin/app/cash/zipline/Call.kt",
      "app/cash/zipline/Call.kt",
    )
    assertCleaned(
      "wire-generated-protos/wire-js/com/squareup/project/protos/File.kt",
      "com/squareup/project/protos/File.kt",
    )

    // From the Kotlin stdlib.
    assertCleaned(
      "src/kotlin/collections/Maps.kt",
      "kotlin/collections/Maps.kt",
    )
    assertCleaned(
      "js-ir/builtins/Char.kt",
      "js-ir/builtins/Char.kt",
    )
    assertCleaned(
      "js-ir/src/generated/_LetterChars.kt",
      "generated/_LetterChars.kt",
    )
    assertCleaned(
      "common/src/generated/_Strings.kt",
      "generated/_Strings.kt",
    )
  }

  private fun assertCleaned(original: String, cleaned: String) {
    assertThat(cleaner.clean(original)).isEqualTo(cleaned)
  }
}
