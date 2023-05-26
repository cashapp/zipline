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

import app.cash.zipline.Zipline
import app.cash.zipline.ZiplineManifest

/**
 * Final result state from a ZiplineLoader attempt.
 */
sealed class LoadResult {
  /**
   * [Zipline] successfully loaded with code and corresponding metadata.
   */
  data class Success(
    val zipline: Zipline,

    /** Manifest that describes the code loaded into [zipline]. */
    val manifest: ZiplineManifest,

    /**
     * Timestamp when this manifest was last known fresh.
     *
     *  * If the manifest came from the network, this is the local time when the request started.
     *  * If the manifest came from the cache, this is the last known fresh time of what was stored.
     *  * If the manifest was embedded, this is the last known fresh time of what was embedded.
     *
     * This timestamp is computed by the local machine for network and cached manifests. For
     * embedded manifests it is computed by whatever machine performed the embedding.
     */
    val freshAtEpochMs: Long,
  ) : LoadResult()

  /**
   * [Exception] from the Zipline code load failure and corresponding metadata.
   */
  data class Failure(
    val exception: Exception,
  ) : LoadResult()
}
