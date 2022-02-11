package app.cash.zipline.loader

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver.Companion.IN_MEMORY
import okio.FileSystem
import okio.Path

actual fun getDriver(path: Path): SqlDriver = JdbcSqliteDriver(IN_MEMORY + path.toString())

fun openZiplineCacheForTesting(
  database: Database,
  fileSystem: FileSystem,
  directory: Path,
  maxSizeInBytes: Long,
  nowMs: () -> Long
): ZiplineCache {
  val ziplineCache = ZiplineCache(
    database = database,
    fileSystem = fileSystem,
    directory = directory,
    maxSizeInBytes = maxSizeInBytes,
    nowMs = nowMs,
  )
  ziplineCache.prune()
  return ziplineCache
}
