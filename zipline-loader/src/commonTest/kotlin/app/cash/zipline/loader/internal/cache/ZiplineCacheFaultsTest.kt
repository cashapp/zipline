/*
 * Copyright (C) 2024 Cash App
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

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isLessThanOrEqualTo
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * This is almost like a fuzz test: in each method it runs many iterations of the same operations,
 * but each iteration injects a failure after a different number of writes.
 *
 * Because the behavior or the file system is different on each run, the test is lenient in what it
 * expects from the cache: it accepts a wide variety of download counts. But regardless of whether
 * the file system accepts writes, the cache should never return invalid results.
 */
class ZiplineCacheFaultsTest {
  @Test
  fun openMissHitClose(): Unit = runBlocking {
    // How many writes are attempted in this test in the happy path. Determined experimentally!
    val noFailuresWriteCount = 23

    for (i in 0..noFailuresWriteCount) {
      val tester = CacheFaultsTester()
      tester.fileSystemWriteLimit = i

      tester.withCache {
        // Initial launch should have 2 cache misses: the Manifest + the Zipline file.
        loadApp()
        assertThat(tester.downloadCount).isEqualTo(2)

        // The subsequent launch should have between 2 and 4 cache misses, depending on what was
        // successfully written to the cache last time.
        loadApp()
        assertThat(tester.downloadCount).isLessThanOrEqualTo(4)
      }

      // Assert more strictly if we didn't need to inject any write failures.
      if (i == noFailuresWriteCount) {
        assertThat(tester.storageFailureCount).isEqualTo(0)
        assertThat(tester.downloadCount).isEqualTo(2)
      }

      assertThat(tester.fileSystemWriteCount).isLessThanOrEqualTo(noFailuresWriteCount)
    }
  }

  @Test
  fun openMissClose_OpenHitClose(): Unit = runBlocking {
    // How many writes are attempted in this test in the happy path. Determined experimentally!
    val noFailuresWriteCount = 24

    for (i in 0..noFailuresWriteCount) {
      val tester = CacheFaultsTester()
      tester.fileSystemWriteLimit = i

      // Initial launch should have 2 cache misses: the Manifest + the Zipline file.
      tester.withCache {
        loadApp()
      }
      assertThat(tester.downloadCount).isEqualTo(2)

      // The subsequent launch should have between 2 and 4 cache misses, depending on what was
      // successfully written to the cache last time.
      tester.withCache {
        loadApp()
      }
      assertThat(tester.downloadCount).isLessThanOrEqualTo(4)

      // Assert more strictly if we didn't need to inject any write failures.
      if (i == noFailuresWriteCount) {
        assertThat(tester.storageFailureCount).isEqualTo(0)
        assertThat(tester.downloadCount).isEqualTo(2)
      }

      assertThat(tester.fileSystemWriteCount).isLessThanOrEqualTo(noFailuresWriteCount)
    }
  }

  @Test
  fun openFailClose_OpenMissClose_OpenHitClose(): Unit = runBlocking {
    // How many writes are attempted in this test in the happy path. Determined experimentally!
    val noFailuresWriteCount = 31

    for (i in 0..noFailuresWriteCount) {
      val tester = CacheFaultsTester()
      tester.fileSystemWriteLimit = i

      // Initial launch should have 2 cache misses: the Manifest + the Zipline file.
      tester.withCache {
        loadApp(loadSuccess = false)
      }
      assertThat(tester.downloadCount).isEqualTo(2)

      // The subsequent launch should have 2 more cache misses.
      tester.manifestVersion++
      tester.withCache {
        loadApp()
      }
      assertThat(tester.downloadCount).isEqualTo(4)

      // A third launch should have between 4 and 6 cache misses, depending on what was successfully
      // written to the cache in round 2.
      tester.withCache {
        loadApp()
      }
      assertThat(tester.downloadCount).isLessThanOrEqualTo(6)

      // Assert more strictly if we didn't need to inject any write failures.
      if (i == noFailuresWriteCount) {
        assertThat(tester.storageFailureCount).isEqualTo(0)
        assertThat(tester.downloadCount).isEqualTo(4)
      }

      assertThat(tester.fileSystemWriteCount).isLessThanOrEqualTo(noFailuresWriteCount)
    }
  }

  /** This test confirms that stale cached results are pruned. */
  @Test
  fun openMissClose_openStaleClose(): Unit = runBlocking {
    // How many writes are attempted in this test in the happy path. Determined experimentally!
    val noFailuresWriteCount = 36

    for (i in 0..noFailuresWriteCount) {
      val tester = CacheFaultsTester()
      tester.fileSystemWriteLimit = i

      // Initial launch should have 2 cache misses: the Manifest + the Zipline file.
      tester.withCache {
        loadApp()
      }
      assertThat(tester.downloadCount).isEqualTo(2)

      // The 2nd launch should have 2 more cache misses because the files have changed. When the
      // updated files are pinned the old files should be pinned.
      tester.cacheSize = 0
      tester.manifestVersion++
      tester.withCache {
        loadApp(cachedResultIsFresh = false)
      }
      assertThat(tester.downloadCount).isEqualTo(4)

      // We expect some combination of these files.
      assertThat(tester.fileNames).each {
        it.isIn(
          "entry-1.bin",
          "entry-2.bin",
          "entry-3.bin",
          "entry-4.bin",
          "zipline.db",
        )
      }

      // Opening the cache triggers pruning.
      tester.withCache {
      }

      // Assert more strictly if we didn't need to inject any write failures.
      if (i == noFailuresWriteCount) {
        assertThat(tester.storageFailureCount).isEqualTo(0)
        assertThat(tester.fileNames).each {
          it.isIn(
            "entry-3.bin",
            "entry-4.bin",
            "zipline.db",
          )
        }
      }

      assertThat(tester.fileSystemWriteCount).isLessThanOrEqualTo(noFailuresWriteCount)
    }
  }
}
