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
package app.cash.zipline.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatabaseNativeTest {
  @Test
  fun `happy path`() {
    val driverFactory = DriverFactory(
      schema = Produce.Schema,
      dbName = "zipline-database-test.db"
    )
    val driver = driverFactory.createDriver()
    val database = Produce(driver)
    assertEquals(0, database.produceQueries.count().executeAsOne())
  }

  @Test
  fun dbPathMustEndWithDb() {
    val exception = assertFailsWith<IllegalArgumentException> {
      DriverFactory(
        schema = Produce.Schema,
        dbName = "zipline-database-test.not-db"
      )
    }
    assertEquals("dbName must end with file suffix .db", exception.message)
  }
}
