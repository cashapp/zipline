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
package app.cash.zipline.loader

import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.db.SqlDriver

// in src/commonMain/kotlin
expect class DriverFactory {
  fun createDriver(): SqlDriver
}

// TODO make internal / upstream to SqlDelight
expect class SQLiteException : SQLException
expect open class SQLException : Exception

fun createDatabase(driverFactory: DriverFactory): Database {
  val driver = driverFactory.createDriver()
  return createDatabase(driver)
}

fun createDatabase(driver: SqlDriver): Database {
  val database = Database(
    driver,
    filesAdapter = Files.Adapter(
      file_stateAdapter = EnumColumnAdapter()
    )
  )
  Database.Schema.create(driver)
  return database
}
