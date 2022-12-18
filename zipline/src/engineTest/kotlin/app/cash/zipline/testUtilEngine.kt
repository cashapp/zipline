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
package app.cash.zipline

import app.cash.zipline.internal.encodeToStringFast
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Yields until [expected] equals the result of [actual].
 *
 * Use this to assert asynchronously triggered side effects, such as resource cleanups.
 */
suspend fun awaitEquals(
  expected: Any?,
  actual: () -> Any?
) {
  var actualValue = actual()
  if (expected == actualValue) return
  for (i in 0 until 5) {
    yield()
    actualValue = actual()
    if (expected == actualValue) return
  }
  throw AssertionError("$expected != $actualValue")
}

fun prettyPrint(jsonString: String): String {
  val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }
  val jsonTree = json.decodeFromString(JsonElement.serializer(), jsonString)
  return json.encodeToStringFast(JsonElement.serializer(), jsonTree)
}
