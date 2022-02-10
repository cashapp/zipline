package app.cash.zipline.loader

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver.Companion.IN_MEMORY
import okio.FileSystem
import okio.Path

actual fun openZiplineCache(
  fileSystem: FileSystem,
  dbPath: Path,
  directory: Path,
  maxSizeInBytes: Long,
): ZiplineCache {
  val driver = JdbcSqliteDriver(IN_MEMORY + dbPath.toString())
  val ziplineCache = ZiplineCache(
    driver = driver,
    fileSystem = fileSystem,
    directory = directory,
    maxSizeInBytes = maxSizeInBytes,
  )
  ziplineCache.prune()
  return ziplineCache
}

fun openZiplineCacheForTesting(
  fileSystem: FileSystem,
  directory: Path,
  maxSizeInBytes: Long,
): ZiplineCache {
  val driver = JdbcSqliteDriver(IN_MEMORY)
  val ziplineCache = ZiplineCache(
    driver = driver,
    fileSystem = fileSystem,
    directory = directory,
    maxSizeInBytes = maxSizeInBytes,
  )
  ziplineCache.prune()
  return ziplineCache
}
