package app.cash.zipline.loader.fetcher

import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.ZiplineHttpClient
import app.cash.zipline.loader.createZiplineCache
import com.squareup.sqldelight.db.SqlDriver
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Fetch from the network and save to local fileSystem cache once downloaded.
 */
class FsCachingFetcher(
  private val cache: ZiplineCache,
  private val delegate: Fetcher,
) : Fetcher {
  constructor(
    cache: ZiplineCache,
    httpClient: ZiplineHttpClient,
  ) : this(
    cache = cache,
    delegate = HttpFetcher(httpClient = httpClient)
  )

  constructor(
    cacheDbDriver: SqlDriver, // SqlDriver is already initialized to the platform and SQLite DB on disk
    cacheDir: Path,
    cacheFileSystem: FileSystem,
    cacheMaxSizeInBytes: Int = 100 * 1024 * 1024, // 100 MiB
    nowMs: () -> Long,
    delegate: Fetcher
  ) : this(
    cache = createZiplineCache(
      driver = cacheDbDriver,
      fileSystem = cacheFileSystem,
      directory = cacheDir,
      maxSizeInBytes = cacheMaxSizeInBytes.toLong(),
      nowMs = nowMs
    ), delegate = delegate
  )

  constructor(
    cacheDbDriver: SqlDriver, // SqlDriver is already initialized to the platform and SQLite DB on disk
    cacheDir: Path,
    cacheFileSystem: FileSystem,
    cacheMaxSizeInBytes: Int = 100 * 1024 * 1024, // 100 MiB
    nowMs: () -> Long,
    httpClient: ZiplineHttpClient,
  ) : this(
    cacheDbDriver = cacheDbDriver,
    cacheDir = cacheDir,
    cacheFileSystem = cacheFileSystem,
    cacheMaxSizeInBytes = cacheMaxSizeInBytes,
    nowMs = nowMs,
    delegate = HttpFetcher(httpClient = httpClient)
  )

  override suspend fun fetch(
    id: String,
    sha256: ByteString,
    url: String,
    fileNameOverride: String?
  ): ByteString? =
    cache.getOrPut(sha256) {
      delegate.fetch(id, sha256, url, fileNameOverride)!!
    }
}
