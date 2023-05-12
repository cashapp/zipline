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

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.interop.SQLiteException
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver
import com.squareup.sqldelight.drivers.native.wrapConnection
import okio.Path

internal actual class SqlDriverFactory {
  actual fun create(path: Path, schema: SqlDriver.Schema): SqlDriver {
    validateDbPath(path)
    return NativeSqliteDriver(
      configuration = DatabaseConfiguration(
        name = path.name,
        version = schema.version,
        create = { connection ->
          wrapConnection(connection) { schema.create(it) }
        },
        upgrade = { connection, oldVersion, newVersion ->
          wrapConnection(connection) { schema.migrate(it, oldVersion, newVersion) }
        },
        extendedConfig = DatabaseConfiguration.Extended(
          basePath = path.parent!!.toString(),
        ),
      ),
    )
  }
}

internal actual fun isSqlException(e: Exception) = e is SQLiteException
