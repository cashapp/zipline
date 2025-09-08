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

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import okio.Path

internal interface SqlDriverFactory {
  /**
   * Create a SqlDriver to be used in creating and managing a SqlLite instance on disk.
   *
   * Database is created and migrated after the driver is initialized prior to return.
   */
  fun create(path: Path, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver
}

/** Identify if an exception is from platform specific SqlLite library */
internal expect fun isSqlException(e: Exception): Boolean

internal fun validateDbPath(path: Path) {
  require(path.name.endsWith(".db")) {
    "path name must end with file suffix .db"
  }
}

internal fun createDatabase(driver: SqlDriver): Database = Database(
    driver,
    filesAdapter = Files.Adapter(
      file_stateAdapter = EnumColumnAdapter(),
    ),
  )
