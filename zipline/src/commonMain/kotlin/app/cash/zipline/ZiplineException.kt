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
package app.cash.zipline

/**
 * Thrown by [ZiplineService] function calls when the target function threw an exception.
 *
 * This is similar to a wrapping exception like Java's `InvocationTargetException`, but the wrapped
 * exception type is not generally available because its class might not exist in the catching
 * process.
 */
class ZiplineException(
  message: String? = null,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
