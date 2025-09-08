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
package app.cash.zipline.loader.internal.cache

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.zipline.loader.LoaderEventListener
import app.cash.zipline.loader.ZiplineCache
import app.cash.zipline.loader.internal.fetcher.LoadedManifest
import app.cash.zipline.loader.randomToken
import app.cash.zipline.loader.testSqlDriverFactory
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createJs
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createRelativeManifest
import app.cash.zipline.testing.systemFileSystem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Sink

class ZiplineCacheTest {
  private val fileSystem = FaultInjectingFileSystem(systemFileSystem)
  private val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "okio-${randomToken().hex()}"
  private val cacheSize = 64
  private var nowMillis = 1_000L
  private lateinit var testFixtures: LoaderTestFixtures

  @BeforeTest
  fun setUp() {
    fileSystem.createDirectories(directory)
    testFixtures = LoaderTestFixtures()
  }

  @Test
  fun readOpensFileThatHasBeenDownloadedOrNullIfNotReady(): Unit = runBlocking {
    withCache { ziplineCache ->
      val fileContents = "abc123".encodeUtf8()
      val fileSha = fileContents.sha256()

      // File not READY.
      assertNull(ziplineCache.read(fileSha))
      assertFalse(fileSystem.exists(directory / fileSha.hex()))

      // File downloaded.
      ziplineCache.write("red", fileSha, fileContents)
      assertTrue(fileSystem.exists(directory / "entry-1.bin"))

      // File can be read.
      assertEquals(fileContents, ziplineCache.read(fileSha))
    }
  }

  @Test
  fun readTriggersDownloadForFileThatIsNotOnFilesystemYet(): Unit = runBlocking {
    withCache { ziplineCache ->
      val fileContents = "abc123".encodeUtf8().sha256()
      val fileSha = fileContents.sha256()

      // File not READY.
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
  fun cachePrunesWhenCapacityExceeded(): Unit = runBlocking {
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
  fun cachePrunesByLeastRecentlyAccessed(): Unit = runBlocking {
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
  fun cacheElementExceedsCacheMaxSize(): Unit = runBlocking {
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
  fun cacheOnOpenPrunesAnyFilesInExcessOfLimit(): Unit = runBlocking {
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
  fun newFilesAreOptimisticallyPinned(): Unit = runBlocking {
    withCache {
      assertEquals(0, it.countFiles())
      assertEquals(0, it.countPins())
      it.write("app1", testFixtures.alphaSha256, testFixtures.alphaByteString)
      assertEquals(1, it.countFiles())
      assertEquals(1, it.countPins())
    }
  }

  @Test
  fun pinRemovesExistingPinsSoOnlyOneManifestIsPinnedPerApplicationName(): Unit = runBlocking {
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
  fun unpinRemovesAllPinsForTheManifest(): Unit = runBlocking {
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
  fun selectPinnedManifestReturnsNewestByFileId(): Unit = runBlocking {
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

      it.updateManifestFreshAt("red", manifestFiretruck, 1)
      assertEquals(4, it.countPins())

      assertEquals(manifestFiretruck, it.getPinnedManifest("red"))
    }
  }

  @Test
  fun recoverAfterDiskWriteFailsBeforeCreatingTheFile(): Unit = runBlocking {
    val fileContents = "abc123".encodeUtf8().sha256()
    val fileSha = fileContents.sha256()

    // Synthesize a failure writing to the cache.
    withCache { ziplineCache ->
      fileSystem.beforeNextWrite = { throw IOException() }
      ziplineCache.write("red", fileSha, fileContents)
    }

    withCache { ziplineCache ->
      // Confirm reading returns no such file.
      assertNull(ziplineCache.read(fileSha))

      // Confirm writing works.
      ziplineCache.write("red", fileSha, fileContents)
      assertEquals(fileContents, ziplineCache.read(fileSha))
    }
  }

  @Test
  fun recoverAfterDiskWriteFailsAfterCreatingTheFile(): Unit = runBlocking {
    val fileContents = "abc123".encodeUtf8().sha256()
    val fileSha = fileContents.sha256()

    // Synthesize a failure writing to the cache.
    withCache { ziplineCache ->
      fileSystem.afterNextWrite = { throw IOException() }
      ziplineCache.write("red", fileSha, fileContents)
    }

    withCache { ziplineCache ->
      // Confirm reading returns no such file. (We don't know that writing completed!)
      assertNull(ziplineCache.read(fileSha))

      // Confirm writing works.
      ziplineCache.write("red", fileSha, fileContents)
      assertEquals(fileContents, ziplineCache.read(fileSha))
    }
  }

  @Test
  fun recoverAfterDiskWriteFailsWhileWritingTheFile(): Unit = runBlocking {
    val fileContents = "abc123".encodeUtf8().sha256()
    val fileSha = fileContents.sha256()

    withCache { ziplineCache ->
      // Synthesize a partial failure where a file is truncated on disk.
      fileSystem.afterNextWrite = { file ->
        val handle = fileSystem.openReadWrite(file)
        handle.resize(handle.size() - 1L)
      }
      ziplineCache.write("red", fileSha, fileContents)
    }

    withCache { ziplineCache ->
      // Confirm reading returns no such file.
      assertNull(ziplineCache.read(fileSha))

      // Confirm writing works.
      ziplineCache.write("red", fileSha, fileContents)
      assertEquals(fileContents, ziplineCache.read(fileSha))
    }
  }

  @Test
  fun manifestFreshAtMs(): Unit = runBlocking {
    val fileContents = testFixtures.manifestByteString

    withCache { ziplineCache ->
      ziplineCache.pinManifest("red", LoadedManifest(fileContents, 5))
      val get1 = assertNotNull(ziplineCache.getPinnedManifest("red"))
      assertEquals(fileContents, get1.manifestBytes)
      assertEquals(5, get1.freshAtEpochMs)

      // Update the freshAt timestamp.
      ziplineCache.updateManifestFreshAt("red", LoadedManifest(fileContents, 10), nowMillis)
      val get2 = assertNotNull(ziplineCache.getPinnedManifest("red"))
      assertEquals(fileContents, get2.manifestBytes)
      assertEquals(10, get2.freshAtEpochMs)
    }
  }

  private fun tick() {
    nowMillis += 1_000L
  }

  private suspend fun <T> withCache(
    cacheSize: Int = this.cacheSize,
    block: suspend (ZiplineCache) -> T,
  ): T {
    val driver = testSqlDriverFactory().create(
      path = directory / "zipline.db",
      schema = Database.Schema,
    )

    val testSqlDriverFactory = object : SqlDriverFactory {
      override fun create(path: Path, schema: SqlSchema<QueryResult.Value<Unit>>) = driver
    }

    val cache = ZiplineCache(
      sqlDriverFactory = testSqlDriverFactory,
      fileSystem = fileSystem,
      directory = directory,
      maxSizeInBytes = cacheSize.toLong(),
      loaderEventListener = LoaderEventListener.None,
    )
    try {
      return block(cache)
    } finally {
      cache.close()
    }
  }

  private class FaultInjectingFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
    var beforeNextWrite: ((Path) -> Unit) = {}
    var afterNextWrite: ((Path) -> Unit) = {}

    override fun sink(file: Path, mustCreate: Boolean): Sink {
      val beforeWriteCallback = beforeNextWrite
      beforeNextWrite = {}
      beforeWriteCallback(file)

      val sink = super.sink(file, mustCreate)
      return object : Sink by sink {
        override fun close() {
          sink.close()

          val afterWriteCallback = afterNextWrite
          afterNextWrite = {}
          afterWriteCallback(file)
        }
      }
    }
  }

  private fun ZiplineCache.read(sha256: ByteString) = read(sha256, nowMillis)

  private suspend fun ZiplineCache.write(
    applicationName: String,
    sha256: ByteString,
    content: ByteString,
  ) = getOrPut(applicationName, sha256, nowMillis) { content }

  private suspend fun ZiplineCache.getOrPut(
    applicationName: String,
    sha256: ByteString,
    download: suspend () -> ByteString,
  ) = getOrPut(applicationName, sha256, nowMillis, download)

  private fun ZiplineCache.getPinnedManifest(applicationName: String) =
    getPinnedManifest(applicationName, nowMillis)

  private fun ZiplineCache.pinManifest(
    applicationName: String,
    loadedManifest: LoadedManifest,
  ) = pinManifest(applicationName, loadedManifest, nowMillis)

  private fun ZiplineCache.unpinManifest(
    applicationName: String,
    loadedManifest: LoadedManifest,
  ) = unpinManifest(applicationName, loadedManifest, nowMillis)
}
