package app.cash.zipline.loader

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver.Companion.IN_MEMORY
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZiplineCacheTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val driver = JdbcSqliteDriver(IN_MEMORY)
  private lateinit var database: Database
  private lateinit var fileSystem: FileSystem
  private val directory = "/zipline/cache".toPath()
  private val cacheSize = 64
  private var nowMillis = 1_000L

  @Before
  fun setUp() {
    fileSystem = FakeFileSystem()
    database = createDatabase(driver)
  }

  @After
  fun tearDown() {
    driver.close()
  }

  @Test
  fun `read opens file that has been downloaded or null if not ready`(): Unit = runBlocking(dispatcher) {
    withCache { ziplineCache ->
      val fileSha = "abc123".encodeUtf8().sha256()
      val fileShaContents = "abc123".encodeUtf8().sha256()

      // File not READY
      assertNull(ziplineCache.read(fileSha))
      assertFalse(fileSystem.exists(directory / fileSha.hex()))

      // File downloaded
      ziplineCache.write(fileSha, fileShaContents)
      assertTrue(fileSystem.exists(directory / "entry-1.bin"))

      // File can be read
      assertEquals(fileShaContents, ziplineCache.read(fileSha))
    }
  }

  @Test
  fun `read triggers download for file that is not on filesystem yet`(): Unit = runBlocking(dispatcher) {
    withCache { ziplineCache ->
      val fileSha = "abc123".encodeUtf8().sha256()
      val fileShaContents = "abc123".encodeUtf8().sha256()

      // File not READY
      assertNull(ziplineCache.read(fileSha))
      assertFalse(fileSystem.exists(directory / fileSha.hex()))

      val result = ziplineCache.getOrPut(fileSha) {
        fileShaContents
      }
      assertEquals(fileShaContents, result)
      assertEquals(fileShaContents, ziplineCache.read(fileSha))
    }
  }

  @Test
  fun `cache prunes when capacity exceeded`(): Unit = runBlocking(dispatcher) {
    withCache { ziplineCache ->
      val a32 = "a".repeat(cacheSize / 2).encodeUtf8()
      val b32 = "b".repeat(cacheSize / 2).encodeUtf8()
      val c32 = "c".repeat(cacheSize / 2).encodeUtf8()
      val a32Hash = a32.sha256()
      val b32Hash = b32.sha256()
      val c32Hash = c32.sha256()

      assertEquals(a32, ziplineCache.getOrPut(a32Hash) { a32 })
      assertNotNull(ziplineCache.read(a32Hash))
      assertNull(ziplineCache.read(b32Hash))
      assertNull(ziplineCache.read(c32Hash))

      assertEquals(b32, ziplineCache.getOrPut(b32Hash) { b32 })
      assertNotNull(ziplineCache.read(a32Hash))
      assertNotNull(ziplineCache.read(b32Hash))
      assertNull(ziplineCache.read(c32Hash))

      assertEquals(c32, ziplineCache.getOrPut(c32Hash) { c32 })
      assertNull(ziplineCache.read(a32Hash))
      assertNotNull(ziplineCache.read(b32Hash))
      assertNotNull(ziplineCache.read(c32Hash))
    }
  }

  @Test
  fun `cache prunes by least recently accessed`(): Unit = runBlocking(dispatcher) {
    withCache { ziplineCache ->
      val a32 = "a".repeat(cacheSize / 2).encodeUtf8()
      val b32 = "b".repeat(cacheSize / 2).encodeUtf8()
      val c32 = "c".repeat(cacheSize / 2).encodeUtf8()
      val a32Hash = a32.sha256()
      val b32Hash = b32.sha256()
      val c32Hash = c32.sha256()

      assertEquals(a32, ziplineCache.getOrPut(a32Hash) { a32 })
      tick()
      assertEquals(b32, ziplineCache.getOrPut(b32Hash) { b32 })
      tick()
      assertEquals(a32, ziplineCache.getOrPut(a32Hash) { error("expected to be cached") })
      tick()
      assertEquals(c32, ziplineCache.getOrPut(c32Hash) { c32 })
      tick()
      assertNotNull(ziplineCache.read(a32Hash))
      assertNull(ziplineCache.read(b32Hash)) // Least recently accessed.
      assertNotNull(ziplineCache.read(c32Hash))
    }
  }

  @Test
  fun `cache element exceeds cache max size`(): Unit = runBlocking(dispatcher) {
    withCache { ziplineCache ->
      val a65 = "a".repeat(cacheSize + 1).encodeUtf8()
      val a65Hash = a65.sha256()

      assertEquals(a65, ziplineCache.getOrPut(a65Hash) { a65 })
      assertNull(ziplineCache.read(a65Hash)) // Immediately evicted
    }
  }

  @Test
  fun `cache on open prunes any files in excess of limit`(): Unit = runBlocking(dispatcher) {
    val a32 = "a".repeat(cacheSize / 2).encodeUtf8()
    val a32Hash = a32.sha256()

    withCache {
      it.write(a32Hash, a32)
      assertNotNull(it.read(a32Hash))
    }

    withCache {
      assertNotNull(it.read(a32Hash))
    }

    withCache(cacheSize / 3) {
      assertNull(it.read(a32Hash))
    }
  }

  private fun tick() {
    nowMillis += 1_000L
  }

  private suspend fun <T> withCache(
    cacheSize: Int = this.cacheSize,
    block: suspend (ZiplineCache) -> T,
  ): T {
    val cache = openZiplineCacheForTesting(database, fileSystem, directory, cacheSize.toLong(), { nowMillis })
    return block(cache)
  }
}
