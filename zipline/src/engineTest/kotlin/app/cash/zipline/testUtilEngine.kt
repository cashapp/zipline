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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
fun prettyPrint(jsonString: String): String {
  val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }
  val jsonTree = json.decodeFromString(JsonElement.serializer(), jsonString)
  return json.encodeToStringFast(JsonElement.serializer(), jsonTree)
}

/**
 * Endpoints exercise different code paths when the receiver suspends. Call this to force a
 * suspend to exercise that code path.
 */
suspend fun forceSuspend() {
  // Yield multiple times to isolate against arbitrary coroutine scheduling.
  repeat(2) { yield() }
}
