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

import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver

actual class DriverFactory(private val context: android.content.Context) {
  actual fun createDriver(
    schema: SqlDriver.Schema
  ): SqlDriver {
    return AndroidSqliteDriver(
      schema = schema,
      context = context,
      name = "zipline-loader.db",
      useNoBackupDirectory = true,
    )
  }

  actual fun <D> createDatabase(
    sqlDriver: SqlDriver,
    schema: SqlDriver.Schema
  ): D {
  }
}

actual fun isSqlException(e: Exception) = e is android.database.SQLException
