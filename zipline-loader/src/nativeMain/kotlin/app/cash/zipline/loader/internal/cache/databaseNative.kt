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

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.interop.SQLiteException
import okio.Path

internal class NativeSqliteDriverFactory : SqlDriverFactory {
  override fun create(path: Path, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver {
    validateDbPath(path)
    return NativeSqliteDriver(
      configuration = DatabaseConfiguration(
        name = path.name,
        version = schema.version.toInt(),
        create = { connection ->
          wrapConnection(connection, Database.Schema::create)
        },
        upgrade = { connection, oldVersion, newVersion ->
          wrapConnection(connection) {
            schema.migrate(it, oldVersion.toLong(), newVersion.toLong())
          }
        },
        extendedConfig = DatabaseConfiguration.Extended(
          basePath = path.parent!!.toString(),
        ),
      ),
    )
  }
}

internal actual fun isSqlException(e: Exception) = e is SQLiteException
