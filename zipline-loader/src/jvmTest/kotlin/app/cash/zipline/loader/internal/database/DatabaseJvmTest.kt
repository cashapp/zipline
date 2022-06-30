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
package app.cash.zipline.loader.internal.database

import com.squareup.sqldelight.db.SqlDriver
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class DatabaseJvmTest {
  private val driverFactory = DriverFactory(Produce.Schema)
  private lateinit var driver: SqlDriver
  private lateinit var database: Produce

  @Before
  fun before() {
    driver = driverFactory.createDriver()
    database = Produce(driver)
  }

  @After
  fun tearDown() {
    driver.close()
  }

  @Test
  fun `happy path`() {
    assertEquals(0, database.produceQueries.count().executeAsOne())
  }

  @Test
  fun `multiple create calls succeed`() {
    driverFactory.createDriver()
    driverFactory.createDriver()
    driverFactory.createDriver()
  }
}
