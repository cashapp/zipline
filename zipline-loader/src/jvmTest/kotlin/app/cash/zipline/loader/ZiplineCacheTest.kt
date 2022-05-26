/*
 * Copyright (C) 2022 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.loader

import app.cash.zipline.QuickJs
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createJs
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createRelativeManifest
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver.Companion.IN_MEMORY
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test

class ZiplineCacheTest {
  private val driver = JdbcSqliteDriver(IN_MEMORY)
  private lateinit var database: Database
  private lateinit var fileSystem: FileSystem
  private val directory = "/zipline/cache".toPath()
  private val cacheSize = 64
  private var nowMillis = 1_000L
  private lateinit var quickJs: QuickJs
  private lateinit var testFixtures: LoaderTestFixtures

  @Before
  fun setUp() {
    fileSystem = FakeFileSystem()
    Database.Schema.create(driver)
    database = createDatabase(driver)

    quickJs = QuickJs.create()
    testFixtures = LoaderTestFixtures(quickJs)
  }

  @After
  fun tearDown() {
    driver.close()
  }

  @Test
  fun `read opens file that has been downloaded or null if not ready`(): Unit = runBlocking {
    withCache { ziplineCache ->
      val fileSha = "abc123".encodeUtf8().sha256()
      val fileContents = "abc123".encodeUtf8().sha256()

      // File not READY
      assertNull(ziplineCache.read(fileSha))
      assertFalse(fileSystem.exists(directory / fileSha.hex()))

      // File downloaded
      ziplineCache.write("red", fileSha, fileContents)
      assertTrue(fileSystem.exists(directory / "entry-1.bin"))

      // File can be read
      assertEquals(fileContents, ziplineCache.read(fileSha))
    }
  }

  @Test
  fun `read triggers download for file that is not on filesystem yet`(): Unit = runBlocking {
    withCache { ziplineCache ->
      val fileSha = "abc123".encodeUtf8().sha256()
      val fileContents = "abc123".encodeUtf8().sha256()

      // File not READY
      assertNull(ziplineCache.read(fileSha))
      assertFalse(fileSystem.exists(directory / fileSha.hex()))

      val result = ziplineCache.getOrPut("app1", fileSha) {
        fileContents
      }
      assertEquals(fileContents, result)
      assertEquals(fileContents, ziplineCache.read(fileSha))
    }
  }

  @Test
  fun `cache prunes when capacity exceeded`(): Unit = runBlocking {
    withCache { ziplineCache ->
      val a32 = "a".repeat(cacheSize / 2).encodeUtf8()
      val b32 = "b".repeat(cacheSize / 2).encodeUtf8()
      val c32 = "c".repeat(cacheSize / 2).encodeUtf8()
      val a32Hash = a32.sha256()
      val b32Hash = b32.sha256()
      val c32Hash = c32.sha256()

      assertEquals(a32, ziplineCache.getOrPut("app1", a32Hash) { a32 })
      ziplineCache.unpin("app1", a32Hash)
      assertNotNull(ziplineCache.read(a32Hash))
      assertNull(ziplineCache.read(b32Hash))
      assertNull(ziplineCache.read(c32Hash))

      assertEquals(b32, ziplineCache.getOrPut("app1", b32Hash) { b32 })
      ziplineCache.unpin("app1", b32Hash)
      assertNotNull(ziplineCache.read(a32Hash))
      assertNotNull(ziplineCache.read(b32Hash))
      assertNull(ziplineCache.read(c32Hash))

      assertEquals(c32, ziplineCache.getOrPut("app1", c32Hash) { c32 })
      ziplineCache.unpin("app1", c32Hash)
      assertNull(ziplineCache.read(a32Hash))
      assertNotNull(ziplineCache.read(b32Hash))
      assertNotNull(ziplineCache.read(c32Hash))
    }
  }

  @Test
  fun `cache prunes by least recently accessed`(): Unit = runBlocking {
    withCache { ziplineCache ->
      val a32 = "a".repeat(cacheSize / 2).encodeUtf8()
      val b32 = "b".repeat(cacheSize / 2).encodeUtf8()
      val c32 = "c".repeat(cacheSize / 2).encodeUtf8()
      val a32Hash = a32.sha256()
      val b32Hash = b32.sha256()
      val c32Hash = c32.sha256()

      assertEquals(a32, ziplineCache.getOrPut("app1", a32Hash) { a32 })
      ziplineCache.unpin("app1", a32Hash)
      tick()
      assertEquals(b32, ziplineCache.getOrPut("app1", b32Hash) { b32 })
      ziplineCache.unpin("app1", b32Hash)
      tick()
      assertEquals(a32, ziplineCache.getOrPut("app1", a32Hash) { error("expected to be cached") })
      ziplineCache.unpin("app1", a32Hash)
      tick()
      assertEquals(c32, ziplineCache.getOrPut("app1", c32Hash) { c32 })
      ziplineCache.unpin("app1", c32Hash)
      tick()
      assertNotNull(ziplineCache.read(a32Hash))
      assertNull(ziplineCache.read(b32Hash)) // Least recently accessed.
      assertNotNull(ziplineCache.read(c32Hash))
    }
  }

  @Test
  fun `cache element exceeds cache max size`(): Unit = runBlocking {
    withCache { ziplineCache ->
      val a65 = "a".repeat(cacheSize + 1).encodeUtf8()
      val a65Hash = a65.sha256()

      assertEquals(a65, ziplineCache.getOrPut("app1", a65Hash) { a65 })
      ziplineCache.unpin("app1", a65Hash)
      ziplineCache.prune()
      assertNull(ziplineCache.read(a65Hash)) // Immediately evicted
    }
  }

  @Test
  fun `cache on open prunes any files in excess of limit`(): Unit = runBlocking {
    val a32 = "a".repeat(cacheSize / 2).encodeUtf8()
    val a32Hash = a32.sha256()

    withCache {
      it.write("app1", a32Hash, a32)
      it.unpin("app1", a32Hash)
      assertNotNull(it.read(a32Hash))
    }

    withCache {
      assertNotNull(it.read(a32Hash))
    }

    withCache(cacheSize / 3) {
      assertNull(it.read(a32Hash))
    }
  }

  @Test
  fun `new files are optimistically pinned`(): Unit = runBlocking {
    withCache {
      assertEquals(0, it.countFiles())
      assertEquals(0, it.countPins())
      it.write("app1", testFixtures.alphaSha256, testFixtures.alphaByteString)
      assertEquals(1, it.countFiles())
      assertEquals(1, it.countPins())
    }
  }

  @Test
  fun `pin removes existing pins so only one manifest is pinned per application name`(): Unit = runBlocking {
    withCache {
      assertEquals(0, it.countFiles())
      assertEquals(0, it.countPins())
      assertNull(it.getPinnedManifest("red"))

      val fileApple = testFixtures.createZiplineFile(createJs("apple"), "apple.js")
      it.write("red", fileApple.sha256(), fileApple)
      val manifestApple = createRelativeManifest("apple", fileApple.sha256())
      it.pinManifest("red", manifestApple)
      assertEquals(manifestApple, it.getPinnedManifest("red"))
      assertEquals(2, it.countFiles())
      assertEquals(2, it.countPins())

      val fileFiretruck = testFixtures.createZiplineFile(createJs("firetruck"), "firetruck.js")
      it.write("red", fileFiretruck.sha256(), fileFiretruck)
      val manifestFiretruck = createRelativeManifest("firetruck", fileFiretruck.sha256())
      assertEquals(3, it.countFiles())
      assertEquals(3, it.countPins())

      it.pinManifest("red", manifestFiretruck)
      assertEquals(manifestFiretruck, it.getPinnedManifest("red"))
      assertEquals(4, it.countFiles()) // apple manifest remains in cache until prune.
      assertEquals(2, it.countPins())
    }
  }

  @Test
  fun `unpin removes all pins for the manifest`(): Unit = runBlocking {
    withCache {
      assertEquals(0, it.countFiles())
      assertEquals(0, it.countPins())
      assertNull(it.getPinnedManifest("red"))

      val fileApple = testFixtures.createZiplineFile(createJs("apple"), "apple.js")
      it.write("red", fileApple.sha256(), fileApple)
      val manifestApple = createRelativeManifest("apple", fileApple.sha256())
      it.pinManifest("red", manifestApple)
      assertEquals(manifestApple, it.getPinnedManifest("red"))
      assertEquals(2, it.countFiles())
      assertEquals(2, it.countPins())

      val fileFiretruck = testFixtures.createZiplineFile(createJs("firetruck"), "firetruck.js")
      it.write("red", fileFiretruck.sha256(), fileFiretruck)
      val manifestFiretruck = createRelativeManifest("firetruck", fileFiretruck.sha256())
      assertEquals(3, it.countFiles())
      assertEquals(3, it.countPins())

      it.unpinManifest("red", manifestFiretruck)
      assertEquals(manifestApple, it.getPinnedManifest("red"))
      assertEquals(3, it.countFiles()) // firetruck manifest isn't saved to file cache.
      assertEquals(2, it.countPins())
    }
  }

  @Test
  fun `select pinned manifest returns newest by file_id`(): Unit = runBlocking {
    withCache {
      val fileApple = testFixtures.createZiplineFile(createJs("apple"), "apple.js")
      it.write("red", fileApple.sha256(), fileApple)
      val manifestApple = createRelativeManifest("apple", fileApple.sha256())
      it.pinManifest("red", manifestApple)

      val fileFiretruck = testFixtures.createZiplineFile(createJs("firetruck"), "firetruck.js")
      it.write("red", fileFiretruck.sha256(), fileFiretruck)
      val manifestFiretruck = createRelativeManifest("firetruck", fileFiretruck.sha256())

      assertEquals(manifestApple, it.getPinnedManifest("red"))
      assertEquals(3, it.countPins())

      val manifestFiretruckByteString = Json
        .encodeToString(ZiplineManifest.serializer(), manifestFiretruck)
        .encodeUtf8()
      it.writeManifest("red", manifestFiretruckByteString.sha256(), manifestFiretruckByteString)
      assertEquals(4, it.countPins())

      assertEquals(manifestFiretruck, it.getPinnedManifest("red"))
    }
  }

  private fun tick() {
    nowMillis += 1_000L
  }

  private suspend fun <T> withCache(
    cacheSize: Int = this.cacheSize,
    block: suspend (ZiplineCache) -> T,
  ): T {
    val cache = openZiplineCacheForTesting(
      database = database,
      fileSystem = fileSystem,
      directory = directory,
      maxSizeInBytes = cacheSize.toLong()
    ) { nowMillis }
    return block(cache)
  }
}
