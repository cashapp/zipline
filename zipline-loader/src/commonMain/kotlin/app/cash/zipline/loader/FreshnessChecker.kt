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
 * Decides whether a locally-cached application is fresh enough to use at the current time.
 *
 * This is unused for downloaded applications, which are considered to be fresh by definition.
 */
interface FreshnessChecker {

  /**
   * Returns true if [manifest] is eligible to be used.
   *
   * @param freshAtEpochMs the time that [manifest] was fetched from the server by this client.
   *     This could be significantly newer than the time the application was built.
   */
  fun isFresh(
    manifest: ZiplineManifest,
    freshAtEpochMs: Long,
  ): Boolean
}

/** A FreshnessChecker that never loads locally-cached applications. */
object DefaultFreshnessCheckerNotFresh : FreshnessChecker {
  override fun isFresh(manifest: ZiplineManifest, freshAtEpochMs: Long): Boolean {
    return false
  }
}
