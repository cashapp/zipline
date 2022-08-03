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

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import java.sql.SQLException
import okio.Path

internal actual class SqlDriverFactory {
  actual fun create(path: Path, schema: SqlDriver.Schema): SqlDriver {
    validateDbPath(path)
    val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:$path")
    try {
      schema.create(driver)
    } catch (ignored: Exception) {
      // Schema already created? (Android and iOS have more robust migration mechanisms).
    }
    return driver
  }
}

internal actual fun isSqlException(e: Exception) = e is SQLException
