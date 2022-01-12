package app.cash.zipline.loader

import com.squareup.sqldelight.db.SqlDriver

// in src/commonMain/kotlin
expect class DriverFactory {
  fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DriverFactory): Database {
  val driver = driverFactory.createDriver()
  val database = Database(driver)
  return database
}
