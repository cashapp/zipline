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

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseFileContext
import co.touchlab.sqliter.native.NativeDatabaseManager
import com.squareup.sqldelight.Transacter
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver
import com.squareup.sqldelight.drivers.native.wrapConnection

actual class DatabaseFactory(
  private val dbPath: String,
  private val schema: SqlDriver.Schema,
) {
  actual fun createDriver(): SqlDriver {
    val basePath = dbPath.substringBeforeLast('/')
    val name = dbPath.substringAfterLast('/')
    val configuration = DatabaseConfiguration(
      extendedConfig = DatabaseConfiguration.Extended(
        basePath = basePath,
      ),
      name = name,
      version = schema.version,
      create = { connection ->
        wrapConnection(connection, schema::create)
      },
      upgrade = { connection, oldVersion, newVersion ->
        wrapConnection(connection) { schema.migrate(it, oldVersion, newVersion) }
      }
    )
    val success = sanityCheck(configuration)

    // Attempt to delete a problematic database
    if (!success)
      DatabaseFileContext.deleteDatabase(name, basePath)

    return NativeSqliteDriver(configuration = configuration)
  }

  actual fun <D: Transacter> createDatabase(
    sqlDriver: SqlDriver,
  ): D {
    return schema.create(sqlDriver) as D
  }

  private fun sanityCheck(configuration: DatabaseConfiguration): Boolean {
    val databaseManager = NativeDatabaseManager(dbPath, configuration)
    val conn = databaseManager.createMultiThreadedConnection()
    var success = true

    // TODO pass in the database table names
    try {
      // If the tables don't exist, createStatement fails
      val stmt = conn.createStatement(
        "SELECT count(*) FROM entity_fts UNION\n" +
          "SELECT count(*) FROM entity_lookup UNION\n" +
          "SELECT count(*) FROM statics"
      )

      stmt.finalizeStatement()
    } catch (e: Exception) {
      reportCrash(e)
      success = false
    } finally {
      conn.close()
    }

    return success
  }
}

// TODO find the native exception class
actual fun isSqlException(e: Exception): Boolean = TODO()
