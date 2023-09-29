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
package app.cash.zipline

import app.cash.zipline.testing.newEndpointPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

internal class EndpointPropertiesTest {
  interface ValService : ZiplineService {
    val count: Int
  }

  interface VarService : ZiplineService {
    var count: Int
  }

  @Test
  fun valProperty() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    var countCalls = 0
    var result = 42
    val service = object : ValService {
      override val count: Int
        get() {
          countCalls++
          return result
        }
    }

    endpointA.bind<ValService>("valService", service)
    val client = endpointB.take<ValService>("valService")

    assertEquals(42, client.count)
    assertEquals(1, countCalls)

    // Confirm every access goes to the source of truth.
    result = 24
    assertEquals(24, client.count)
    assertEquals(2, countCalls)
  }

  @Test
  fun varProperty() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    var countCalls = 0
    var state = 42

    val service = object : VarService {
      override var count: Int
        get() = error("unexpected call")
        set(value) {
          countCalls++
          state = value
        }
    }

    endpointA.bind<VarService>("varService", service)
    val client = endpointB.take<VarService>("varService")

    // Confirm setter changes state.
    client.count = 24
    assertEquals(1, countCalls)
    assertEquals(24, state)
  }

  interface GenericValService<T> : ZiplineService {
    val foo: T
  }

  interface GenericVarService<T> : ZiplineService {
    var foo: T
  }

  @Serializable
  data class Bar(
    val alpha: Boolean,
    val bravo: String,
    val charlie: Baz,
  )

  @Serializable
  enum class Baz {
    BLEEP,
    BLOOP,
    BONGO,
    BINGO,
  }

  @Test
  fun genericValProperty() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    var fooCalls = 0
    var result = Bar(
      alpha = true,
      bravo = "bingo",
      charlie = Baz.BINGO,
    )

    val service = object : GenericValService<Bar> {
      override val foo: Bar
        get() {
          fooCalls++
          return result
        }
    }

    endpointA.bind<GenericValService<Bar>>("genericValService", service)
    val client = endpointB.take<GenericValService<Bar>>("genericValService")

    assertEquals(
      Bar(
      alpha = true,
      bravo = "bingo",
      charlie = Baz.BINGO,
    ),
      client.foo,
    )
    assertEquals(1, fooCalls)

    // Confirm every access goes to the source of truth.
    result = Bar(
      alpha = false,
      bravo = "bloop",
      charlie = Baz.BLOOP,
    )
    assertEquals(
      Bar(
      alpha = false,
      bravo = "bloop",
      charlie = Baz.BLOOP,
    ),
      client.foo,
    )
    assertEquals(2, fooCalls)
  }

  @Test
  fun genericVarProperty() = runBlocking(Unconfined) {
    val (endpointA, endpointB) = newEndpointPair(this)

    var fooCalls = 0
    var state = Bar(
      alpha = true,
      bravo = "bingo",
      charlie = Baz.BINGO,
    )

    val service = object : GenericVarService<Bar> {
      override var foo: Bar
        get() = error("unexpected call")
        set(value) {
          fooCalls++
          state = value
        }
    }

    endpointA.bind<GenericVarService<Bar>>("genericVarService", service)
    val client = endpointB.take<GenericVarService<Bar>>("genericVarService")

    // Confirm setter changes state.
    client.foo = Bar(
      alpha = false,
      bravo = "bloop",
      charlie = Baz.BLOOP,
    )
    assertEquals(1, fooCalls)
    assertEquals(
      Bar(
      alpha = false,
      bravo = "bloop",
      charlie = Baz.BLOOP,
    ),
      state,
    )
  }
}
