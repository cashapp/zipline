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
package app.cash.zipline.tests

import app.cash.zipline.Zipline
import app.cash.zipline.ZiplineService
import kotlinx.coroutines.Dispatchers

internal interface UnusedService : ZiplineService {
  fun unusedMethod()
}

fun main() {
  val zipline = Zipline.create(Dispatchers.Default)
  zipline.take<UnusedService>("takenService")
  zipline.bind<UnusedService>(
    "boundService",
    object : UnusedService {
    override fun unusedMethod() {
    }
  },
  )

  // If we reach this line without crashing, bind() and take() were rewritten. (If the Zipline
  // plugin is not installed we'll get an error like "is the Zipline plugin configured?")
  println("Zipline Kotlin plugin did its job properly")
}
