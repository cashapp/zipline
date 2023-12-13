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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours


/** Checks if a given ZiplineManifest is considered fresh, therefore safe to be served. */
interface FreshnessChecker {

  /** Returns true if the manifest is considered fresh. */
  fun isFresh(
    manifest: ZiplineManifest,
    freshAtEpochMs: Long,
    //shelfLife: Duration = 168.hours
  ): Boolean
}
