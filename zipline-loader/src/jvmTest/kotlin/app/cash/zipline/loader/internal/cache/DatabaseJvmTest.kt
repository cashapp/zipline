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

import app.cash.sqldelight.db.SqlDriver
import app.cash.zipline.loader.internal.cache.testing.Produce
import kotlin.test.assertEquals
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseJvmTest {
  @JvmField @Rule
  val temporaryFolder = TemporaryFolder()

  private val sqlDriverFactory = JdbcSqliteDriverFactory()
  private lateinit var driver: SqlDriver
  private lateinit var database: Produce

  @Before
  fun before() {
    driver = sqlDriverFactory.create(
      path = temporaryFolder.root.toOkioPath() / "zipline.db",
      schema = Produce.Schema,
    )
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
    val driver1 = sqlDriverFactory.create(
      path = temporaryFolder.root.toOkioPath() / "database1.db",
      schema = Produce.Schema,
    )
    val driver2 = sqlDriverFactory.create(
      path = temporaryFolder.root.toOkioPath() / "database2.db",
      schema = Produce.Schema,
    )
    val driver3 = sqlDriverFactory.create(
      path = temporaryFolder.root.toOkioPath() / "database3.db",
      schema = Produce.Schema,
    )
    driver1.close()
    driver2.close()
    driver3.close()
  }
}
