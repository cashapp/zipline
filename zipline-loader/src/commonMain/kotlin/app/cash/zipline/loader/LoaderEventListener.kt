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
package app.cash.zipline.loader

abstract class LoaderEventListener {
  /**
   * Invoked each time accessing the cache fails, such as due to a full file system.
   *
   * After any failure to write to storage, the cache enters a degraded mode where all reads
   * return cache misses and all writes do nothing. The only way to recover from a degraded cache
   * is to close the instance and open another one. This occurs naturally when the host
   * application is restarted.
   *
   * @param applicationName the application that triggered this cache access, or null if this
   *     access was for a shared resource, such as during cache initialization.
   * @param e the exception that triggered this failure. This is typically either a file system
   *     write error or a metadata database write error.
   */
  open fun cacheStorageFailed(
    applicationName: String?,
    e: Exception,
  ) {
  }

  companion object {
    val NONE: LoaderEventListener = object : LoaderEventListener() {
    }
  }
}
