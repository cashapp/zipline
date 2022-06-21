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

import com.squareup.sqldelight.db.SqlDriver

expect class DriverFactory {
  /**
   * Create a SqlDriver to be used in creating and managing a SqlLite instance on disk.
   *
   * Database is created and migrated after the driver is initialized prior to return.
   */
  fun createDriver(): SqlDriver
}

/** Identify if an exception is from platform specific SqlLite library */
expect fun isSqlException(e: Exception): Boolean

fun validateDbName(dbName: String) {
  require(dbName.endsWith(".db")) {
    "dbName must end with file suffix .db"
  }
}
