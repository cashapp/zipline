package app.cash.zipline.loader.strategy

import app.cash.zipline.QuickJs
import app.cash.zipline.loader.FakeZiplineHttpClient
import app.cash.zipline.loader.ZiplineFile.Companion.toZiplineFile
import app.cash.zipline.loader.alphaBytecode
import app.cash.zipline.loader.alphaFilePath
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class DownloadOnlyStrategyTest {
  private val httpClient = FakeZiplineHttpClient()
  private val cacheDbDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

  private var concurrentDownloadsSemaphore = Semaphore(3)

  private lateinit var fileSystem: FileSystem
  private val downloadDirectory = "/zipline/downloads".toPath()
  private lateinit var quickJs: QuickJs

  private lateinit var strategy: LoadStrategy

  @BeforeTest
  fun setUp() {
    quickJs = QuickJs.create()
    fileSystem = FakeFileSystem()
    strategy = DownloadOnlyStrategy(
      httpClient = httpClient,
      concurrentDownloadsSemaphore = concurrentDownloadsSemaphore,
      fileSystem = fileSystem,
      downloadDirectory = downloadDirectory,
    )
  }

  @AfterTest
  fun tearDown() {
    quickJs.close()
    cacheDbDriver.close()
  }

  @Test
  fun getFileFromNetwork(): Unit = runBlocking {
    httpClient.filePathToByteString = mapOf(
      alphaFilePath to alphaBytecode(quickJs),
    )

    val ziplineFile = strategy.getZiplineFile(
      id = "alpha",
      sha256 = alphaBytecode(quickJs).sha256(),
      url = alphaFilePath
    )

    assertEquals(alphaBytecode(quickJs).toZiplineFile(), ziplineFile)
  }


  @Test
  fun processFileWritesToDownloadDirectory(): Unit = runBlocking {
    val alphaByteString = alphaBytecode(quickJs)
    httpClient.filePathToByteString = mapOf(
      alphaFilePath to alphaByteString,
    )

    val ziplineFile = alphaByteString.toZiplineFile()
    strategy.processFile(
      ziplineFile = ziplineFile,
      id = "alpha",
      sha256 = alphaByteString.sha256(),
    )

    assertEquals(ziplineFile, ziplineFile)
  }
}
