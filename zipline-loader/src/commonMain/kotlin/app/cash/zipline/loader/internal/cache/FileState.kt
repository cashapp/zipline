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

enum class FileState {
  /**
   * The file is either currently downloading, or left over from a previous process.
   *
   * If it's currently downloading, it'll transition to `READY` when the download completes.
   * Or the file will be removed if the download fails.
   *
   * If it's left over from a previous process, that's the same as a failed download.
   * Such files should be deleted when the cache is opened.
   */
  DIRTY,

  /**
   * The file is on the file system and ready to read.
   * It will not be modified until it is deleted.
   */
  READY,
}
