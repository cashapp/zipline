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

import android.content.Context
import app.cash.zipline.loader.internal.cache.AndroidSqliteDriverFactory
import okio.FileSystem
import okio.Path

fun ZiplineCache(
  context: Context,
  fileSystem: FileSystem,
  directory: Path,
  maxSizeInBytes: Long,
  loaderEventListener: LoaderEventListener,
): ZiplineCache {
  return ZiplineCache(
    sqlDriverFactory = AndroidSqliteDriverFactory(context),
    fileSystem = fileSystem,
    directory = directory,
    maxSizeInBytes = maxSizeInBytes,
    loaderEventListener = loaderEventListener,
  )
}

fun ZiplineCache(
  context: Context,
  fileSystem: FileSystem,
  directory: Path,
  maxSizeInBytes: Long,
): ZiplineCache {
  return ZiplineCache(
    context = context,
    fileSystem = fileSystem,
    directory = directory,
    maxSizeInBytes = maxSizeInBytes,
    loaderEventListener = LoaderEventListener.None,
  )
}
