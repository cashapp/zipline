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
package app.cash.zipline.loader.internal.cache

import app.cash.zipline.loader.internal.cache.testing.Produce
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.FileSystem
import okio.Path.Companion.toPath

class DatabaseNativeTest {
  private val driverPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "zipline.db"

  @BeforeTest
  fun setUp() {
    FileSystem.SYSTEM.delete(driverPath, mustExist = false)
  }

  @Test
  fun `happy path`() {
    val sqlDriverFactory = NativeSqliteDriverFactory()
    val driver = sqlDriverFactory.create(
      path = driverPath,
      schema = Produce.Schema,
    )
    val database = Produce(driver)
    Produce.Schema.create(driver)
    assertEquals(0, database.produceQueries.count().executeAsOne())
    driver.close()
  }

  @Test
  fun dbPathMustEndWithDb() {
    val sqlDriverFactory = NativeSqliteDriverFactory()
    val exception = assertFailsWith<IllegalArgumentException> {
      sqlDriverFactory.create(
        path = "zipline-database-test.not-db".toPath(),
        schema = Produce.Schema,
      )
    }
    assertEquals("path name must end with file suffix .db", exception.message)
  }
}
