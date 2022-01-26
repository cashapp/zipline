package app.cash.zipline.loader

import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNull
import junit.framework.Assert.assertTrue
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test

class ZiplineCacheTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  private val db = createDatabase(driver)
  private val fileSystem = FakeFileSystem()
  private val ziplineCache = ZiplineCache(
    fileSystem = FakeFileSystem(),
    directory = "/zipline/cache".toPath(),
    database = db,
  )

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

    //
    assertNull(ziplineCache.getOrNull(fileSha)?.file_state)
    assertFalse(ziplineCache.setReady(fileSha, 100L))

    // Set dirty
    assertTrue(ziplineCache.setDirty(fileSha))
    assertTrue(ziplineCache.setReady(fileSha, 100L))

  }
}
