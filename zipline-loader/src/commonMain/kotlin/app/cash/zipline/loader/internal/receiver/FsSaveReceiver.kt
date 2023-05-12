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
package app.cash.zipline.loader.internal.receiver

import app.cash.zipline.loader.ZiplineFile
import okio.ByteString
import okio.FileSystem
import okio.Path

/**
 * Save [ZiplineFile] to file system.
 */
internal class FsSaveReceiver(
  private val downloadFileSystem: FileSystem,
  private val downloadDir: Path,
) : Receiver {
  override suspend fun receive(byteString: ByteString, id: String, sha256: ByteString) {
    downloadFileSystem.write(downloadDir / sha256.hex()) {
      write(byteString)
    }
  }
}
