package app.cash.zipline.loader.strategy

import app.cash.zipline.QuickJs
import app.cash.zipline.Zipline
import app.cash.zipline.loader.FakeZiplineHttpClient
import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
import app.cash.zipline.loader.alphaBytecode
import app.cash.zipline.loader.alphaFilePath
import app.cash.zipline.loader.bravoBytecode
import app.cash.zipline.loader.bravoFilePath
import app.cash.zipline.loader.createZiplineCache
import app.cash.zipline.loader.manifest
import app.cash.zipline.loader.manifestPath
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class EmbeddedCacheThenNetworkStrategyTest {
  private val httpClient = FakeZiplineHttpClient()
  private val dispatcher = TestCoroutineDispatcher()
  private val cacheDbDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val cacheMaxSizeInBytes = 100 * 1024 * 1024
  private val cacheDirectory = "/zipline/cache".toPath()
  private var nowMillis = 1_000L

  private var concurrentDownloadsSemaphore = Semaphore(3)
  private val zipline = Zipline.create(dispatcher)

  private lateinit var cache: ZiplineCache

  private lateinit var fileSystem: FileSystem
  private lateinit var embeddedFileSystem: FileSystem
  private val embeddedDirectory = "/zipline".toPath()
  private lateinit var quickJs: QuickJs

  private lateinit var strategy: LoadStrategy

  @BeforeTest
  fun setUp() {
    quickJs = QuickJs.create()
    fileSystem = FakeFileSystem()
    embeddedFileSystem = FakeFileSystem()
    cache = createZiplineCache(
      driver = cacheDbDriver,
      fileSystem = fileSystem,
      directory = cacheDirectory,
      maxSizeInBytes = cacheMaxSizeInBytes.toLong(),
      nowMs = { nowMillis }
    )
    strategy = EmbeddedCacheThenNetworkStrategy(
      zipline = zipline,
      httpClient = httpClient,
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      embeddedFileSystem = embeddedFileSystem,
      embeddedDirectory = embeddedDirectory,
      cache = cache
    )
  }

  @AfterTest
  fun tearDown() {
    quickJs.close()
    cacheDbDriver.close()
  }

  @Test
  fun getFromEmbeddedFileSystemNoNetworkCall(): Unit = runBlocking {
    embeddedFileSystem.createDirectories(embeddedDirectory)
    val alphaSha256 = alphaBytecode(quickJs).sha256()
    embeddedFileSystem.write(embeddedDirectory / alphaSha256.hex()) {
      write(alphaBytecode(quickJs))
    }

    httpClient.filePathToByteString = mapOf()

    val ziplineFile = strategy.getZiplineFile(
      id = "alpha",
      sha256 = alphaSha256,
      url = alphaFilePath
    )

    assertEquals(alphaBytecode(quickJs).toZiplineFile(), ziplineFile)
  }

  @Test
  fun getFromWarmCacheNoNetworkCall(): Unit = runBlocking {
    val alphaSha256 = alphaBytecode(quickJs).sha256()
    cache.getOrPut(alphaSha256) {
      alphaBytecode(quickJs)
    }

    httpClient.filePathToByteString = mapOf()

    val ziplineFile = strategy.getZiplineFile(
      id = "alpha",
      sha256 = alphaBytecode(quickJs).sha256(),
      url = alphaFilePath
    )

    assertEquals(alphaBytecode(quickJs).toZiplineFile(), ziplineFile)
  }

  @Test
  fun getFromNetworkPutInCache(): Unit = runBlocking {
    val alphaSha256 = alphaBytecode(quickJs).sha256()
    httpClient.filePathToByteString = mapOf(
      alphaFilePath to alphaBytecode(quickJs),
    )

    val ziplineFile = strategy.getZiplineFile(
      id = "alpha",
      sha256 = alphaBytecode(quickJs).sha256(),
      url = alphaFilePath
    )

    assertEquals(alphaBytecode(quickJs).toZiplineFile(), ziplineFile)

    val ziplineFileFromCache = cache.getOrPut(alphaSha256) {
      "fake".encodeUtf8()
    }
    assertEquals(alphaBytecode(quickJs), ziplineFileFromCache)
  }

  @Test
  fun processFileLoadsIntoZipline(): Unit = runBlocking(dispatcher) {
    val alphaByteString = alphaBytecode(quickJs)
    val ziplineFile = alphaByteString.toZiplineFile()
    strategy.processFile(ziplineFile, "alpha", alphaByteString.sha256())
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |""".trimMargin()
    )
  }
}
