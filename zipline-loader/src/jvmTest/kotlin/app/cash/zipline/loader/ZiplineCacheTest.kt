package app.cash.zipline.loader

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test

class ZiplineCacheTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private lateinit var fileSystem: FileSystem
  private lateinit var ziplineCache: ZiplineCache
  private val directory = "/zipline/cache".toPath()

  @Before
  fun setUp() {
    fileSystem = FakeFileSystem()
    ziplineCache = openZiplineCache(driver, fileSystem, directory)
  }

  @After
  fun tearDown() {
    ziplineCache.close()
  }

  @Test
  fun `read opens file that has been downloaded or null if not ready`() {
    val fileSha = "abc123".encodeUtf8().sha256()
    val fileShaContents = "abc123".encodeUtf8().sha256()

    // File not READY
    assertNull(ziplineCache.read(fileSha))
    assertFalse(fileSystem.exists(directory / fileSha.hex()))

    // File downloaded
    ziplineCache.write(fileSha, fileShaContents)
    assertTrue(fileSystem.exists(directory / fileSha.hex()))





  }

  @Test
  fun `read triggers download for file that is not on filesystem yet`() {
    TODO("Not yet implemented")
  }

  @Test
  fun `set dirty of dirty file fails and returns false`() {
    val fileSha = "abc123".encodeUtf8().sha256()

    // Confirm no existing record
    assertNull(ziplineCache.getOrNull(fileSha)?.file_state)

    // Set as dirty
    assertTrue(ziplineCache.setDirty(fileSha))
    assertEquals(FileState.DIRTY, ziplineCache.getOrNull(fileSha)?.file_state)

    // Fail to set as dirty again
    assertFalse(ziplineCache.setDirty(fileSha))
    assertEquals(FileState.DIRTY, ziplineCache.getOrNull(fileSha)?.file_state)
  }

  @Test
  fun `set ready fails if dirty record not present`() {
    val fileSha = "abc123".encodeUtf8().sha256()

    // Confirm that setReady on a file that is absent fails
    assertNull(ziplineCache.getOrNull(fileSha)?.file_state)
    assertFalse(ziplineCache.setReady(fileSha, 100L))

    // Set dirty
    assertTrue(ziplineCache.setDirty(fileSha))
    assertTrue(ziplineCache.setReady(fileSha, 100L))
  }

  @Test
  fun `only dirty file record can be deleted by deleteDirty`() {
    val fileSha = "abc123".encodeUtf8().sha256()

    // Confirm deleteDirty removes file record
    assertTrue(ziplineCache.setDirty(fileSha))
    assertTrue(ziplineCache.deleteDirty(fileSha))
    assertNull(ziplineCache.getOrNull(fileSha)?.file_state)

    // deleteDirty fails on ready file
    assertTrue(ziplineCache.setDirty(fileSha))
    assertTrue(ziplineCache.setReady(fileSha, 100L))
    assertFalse(ziplineCache.deleteDirty(fileSha))
  }

  @Test
  fun `deleteReady only deletes ready files`() {
    val fileSha = "abc123".encodeUtf8().sha256()

    // Confirm deleteReady removes file record
    assertTrue(ziplineCache.setDirty(fileSha))
    assertTrue(ziplineCache.setReady(fileSha, 100L))
    assertTrue(ziplineCache.deleteReady(fileSha))
    assertNull(ziplineCache.getOrNull(fileSha)?.file_state)

    // deleteDirty fails on dirty file
    assertTrue(ziplineCache.setDirty(fileSha))
    assertFalse(ziplineCache.deleteReady(fileSha))
  }

  @Test
  fun `prune removes oldest files until it is within cache size limit`() {
    val fileAlphaSha = "alpha123".encodeUtf8().sha256() // 10 bytes
    val fileBravoSha = "bravo123".encodeUtf8().sha256() // 10 bytes
    val cacheLimit = 15 // 15 bytes
    ziplineCache = ZiplineCache(
      fileSystem = fileSystem,
      directory = "/zipline/cache".toPath(),
      database = db,
      maxSizeInBytes = cacheLimit,
    )

    // Both files are downloaded and ready, cache is at 20/15 byte capacity
    assertTrue(ziplineCache.setDirty(fileAlphaSha))
    assertTrue(ziplineCache.setReady(fileAlphaSha, 10L))
    assertTrue(ziplineCache.setDirty(fileBravoSha))
    assertTrue(ziplineCache.setReady(fileBravoSha, 10L))

    // Run pruning, Alpha file is removed since it is oldest added and the cache is beyond limit
    ziplineCache.prune()
    assertEquals(FileState.READY, ziplineCache.getOrNull(fileBravoSha)?.file_state)
    assertNull(ziplineCache.getOrNull(fileAlphaSha)?.file_state)
  }
}
