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
 * A single invocation of a function declared on a [ZiplineService].
 */
interface ZiplineCall {
  /** The name of the service as used in [Zipline.bind] or [Zipline.take]. */
  val serviceName: String

  /**
   * The service instance. Do not downcast this to a concrete type; it may be a generated
   * implementation of the interface.
   */
  val service: ZiplineService

  /** The signature of the function that was invoked. */
  val functionName: String

  /**
   * The original arguments passed to the function, in the order they appear in the function
   * signature.
   */
  val args: List<*>
}
