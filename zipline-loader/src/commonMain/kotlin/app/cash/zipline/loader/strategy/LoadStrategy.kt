/*
 * Copyright (C) 2021 Square, Inc.
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
package app.cash.zipline.loader.strategy

import app.cash.zipline.loader.ZiplineFile
import okio.ByteString

/**
 * The ZiplineLoader will use a given [LoadStrategy] to process the dependency sorted modules.
 */
interface LoadStrategy {
  /**
   * Get the module's [ZiplineFile].
   *
   * Implementations could get from network, resources, or cache as desired.
   */
  suspend fun getZiplineFile(
    id: String,
    sha256: ByteString,
    url: String
  ): ZiplineFile

  /**
   * Called once the [ZiplineFile] is retrieved to save to disk, seed cache, or load into a
   * Zipline instance
   */
  suspend fun processFile(
    ziplineFile: ZiplineFile,
    id: String,
    sha256: ByteString
  )
}
