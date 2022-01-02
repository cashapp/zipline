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
package app.cash.zipline

/**
 * Implemented by all interfaces that can safely be passed from the host platform to Kotlin/JS or
 * vice versa.
 *
 * When an instance of a service is passed between platforms, the receiving platform **must** call
 * [close] when it is done. This releases the handle on the inbound side, and prevents a resource
 * leak. It is an error to call any method on a service after it is closed.
 */
interface ZiplineService {
  fun close() {
  }
}
