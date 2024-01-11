/*
 * Copyright (C) 2023 Cash App
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

import app.cash.zipline.ZiplineManifest

/**
 * Checks if a given ZiplineManifest is considered fresh.
 *
 * For local development that requires continuous loading (or hot loading), supply a
 * FreshnessChecker that always returns false.
 */
interface FreshnessChecker {

  /**
   * Decides whether the [manifest] is eligible to be used.
   *
   * Returns true to launch the [manifest] immediately; false to download a fresh
   * ZiplineManifest and launch that.
   */
  fun isFresh(
    manifest: ZiplineManifest,
    freshAtEpochMs: Long,
  ): Boolean
}

/** A FreshnessChecker that always returns false. */
object DefaultFreshnessCheckerNotFresh : FreshnessChecker {
  override fun isFresh(manifest: ZiplineManifest, freshAtEpochMs: Long): Boolean {
    return false
  }
}
