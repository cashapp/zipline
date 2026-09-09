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
package app.cash.zipline.loader.internal.cache

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import app.cash.zipline.loader.LoaderEventListener
import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.randomToken
import app.cash.zipline.loader.testSqlDriverFactory
import app.cash.zipline.testing.systemFileSystem
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path

/**
 * Regression test for a use-after-free crash (EXC_BAD_ACCESS in `sqlite3_reset`) that happened when
 * [ZiplineCache.close] tore down the SQLite driver while another thread was still executing a query
 * against it. On iOS this raced during sign-out: the Treehouse/Zipline cache was closed while a code
 * load was still reading from the cache on a background worker thread.
 *
 * [ZiplineCache.close] must acquire the same lock as the database operations, so it waits for any
 * in-flight operation to finish before closing the driver.
 */
class ZiplineCacheConcurrencyTest {
  private val fileSystem = systemFileSystem
  private val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "okio-${randomToken().hex()}"

  @Test
  fun closeWaitsForInFlightDatabaseOperation(): Unit = runBlocking {
    fileSystem.createDirectories(directory)

    val instrumentedDriver = InstrumentedDriver(
      testSqlDriverFactory().create(
        path = directory / "zipline.db",
        schema = Database.Schema,
      ),
    )
    val cache = ZiplineCache(
      sqlDriverFactory = object : SqlDriverFactory {
        override fun create(path: Path, schema: SqlSchema<QueryResult.Value<Unit>>) =
          instrumentedDriver
      },
      fileSystem = fileSystem,
      directory = directory,
      maxSizeInBytes = 1024L,
      loaderEventListener = LoaderEventListener.None,
    )

    // Seed the cache with a readable entry. Instrumentation is off for this.
    val fileContents = "abc123".encodeUtf8()
    val fileSha = fileContents.sha256()
    cache.getOrPut("app1", fileSha, 1_000L) { fileContents }

    // Arm the instrumentation so the next query holds the database "in flight" long enough for a
    // concurrent close() to race with it.
    instrumentedDriver.blockNextQuery = true

    // Run a read on a background thread; it will enter the driver and stay there for ~2s.
    val readJob = launch(Dispatchers.Default) {
      cache.read(fileSha, 1_000L)
    }

    // Wait until the read is actually executing inside the driver.
    val spinUntil = TimeSource.Monotonic.markNow()
    while (!instrumentedDriver.queryInFlight && spinUntil.elapsedNow() < 10.seconds) {
      // Busy-wait for the background read to reach the driver.
    }
    assertTrue(instrumentedDriver.queryInFlight, "the background read never reached the driver")

    // Close the cache while the read is still in flight. With the fix this blocks until the read
    // completes; without it, the driver is closed from under the in-flight query.
    cache.close()

    assertFalse(
      instrumentedDriver.closedWhileQueryInFlight,
      "driver was closed while a query was still executing (use-after-free)",
    )

    readJob.join()
  }

  /**
   * Delegates to a real [SqlDriver] but can hold a single query "in flight" and records whether
   * [close] was ever called while a query was executing.
   */
  private class InstrumentedDriver(
    private val delegate: SqlDriver,
  ) : SqlDriver by delegate {
    @Volatile var blockNextQuery = false
    @Volatile var queryInFlight = false
    @Volatile var closedWhileQueryInFlight = false

    override fun <R> executeQuery(
      identifier: Int?,
      sql: String,
      mapper: (SqlCursor) -> QueryResult<R>,
      parameters: Int,
      binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
      if (!blockNextQuery) {
        return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
      }
      blockNextQuery = false
      queryInFlight = true
      try {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < 2.seconds) {
          // Keep the query "executing" to widen the race window.
        }
        return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
      } finally {
        queryInFlight = false
      }
    }

    override fun close() {
      if (queryInFlight) closedWhileQueryInFlight = true
      delegate.close()
    }
  }
}
