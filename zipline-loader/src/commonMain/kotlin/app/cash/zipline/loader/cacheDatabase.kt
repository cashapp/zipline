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
  val database = Database(
    driver,
    filesAdapter = Files.Adapter(
      file_stateAdapter = EnumColumnAdapter()
    )
  )
  return database
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
