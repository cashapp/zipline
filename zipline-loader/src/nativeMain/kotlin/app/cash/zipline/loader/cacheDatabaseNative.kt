package app.cash.zipline.loader

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.drivers.native.NativeSqliteDriver

actual class DriverFactory {
  actual fun createDriver(): SqlDriver {
    return NativeSqliteDriver(
      schema = Database.Schema,
      name = "zipline-loader.db",
    )
  }
}

// TODO find the native exception class
actual fun isSqlException(e: Exception): Boolean = TODO()
