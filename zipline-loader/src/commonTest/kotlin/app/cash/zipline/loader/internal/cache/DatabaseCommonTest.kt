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
import app.cash.zipline.loader.randomToken
import app.cash.zipline.loader.testSqlDriverFactory
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.testing.systemFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem

class DatabaseCommonTest {
  private lateinit var driver: SqlDriver
  private lateinit var database: Database
  private val fileSystem = systemFileSystem
  private val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "okio-${randomToken().hex()}"
  private lateinit var testFixtures: LoaderTestFixtures

  @BeforeTest
  fun setUp() {
    fileSystem.createDirectories(directory)
    driver = testSqlDriverFactory().create(
      path = directory / "zipline.db",
      schema = Database.Schema,
    )
    database = createDatabase(driver)
    testFixtures = LoaderTestFixtures()
  }

  @AfterTest
  fun tearDown() {
    driver.close()
  }

  @Test
  fun insertCollisionThrowsSQLiteException() {
    val manifestForApplicationName = "app1"
    val sha256 = "hello".encodeUtf8().sha256()

    // Insert a row into our empty DB.
    database.filesQueries.insert(
      sha256_hex = sha256.hex(),
      manifest_for_application_name = manifestForApplicationName,
      file_state = FileState.DIRTY,
      size_bytes = 0L,
      last_used_at_epoch_ms = 1,
      fresh_at_epoch_ms = 1,
    )

    // Inserting another row with the same sha256_hex should fail!
    val exception = assertFailsWith<Exception> {
      database.filesQueries.insert(
        sha256_hex = sha256.hex(),
        manifest_for_application_name = manifestForApplicationName,
        file_state = FileState.DIRTY,
        size_bytes = 0L,
        last_used_at_epoch_ms = 1,
        fresh_at_epoch_ms = 1,
      )
    }

    assertTrue(isSqlException(exception))
  }
}
