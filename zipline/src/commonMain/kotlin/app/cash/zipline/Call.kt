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
class Call(
  /** The name of the service as used in [Zipline.bind] or [Zipline.take]. */
  val serviceName: String,

  /**
   * The service instance. Do not downcast this to a concrete type; it may be a generated
   * implementation of the interface.
   */
  val service: ZiplineService,

  /**
   * The function being called. If the function is unknown to the receiver, it will synthesize a
   * [ZiplineFunction] instance that always throws [ZiplineApiMismatchException].
   */
  val function: ZiplineFunction<*>,

  /**
   * The original arguments passed to the function, in the order they appear in the function
   * signature.
   */
  val args: List<*>,

  /**
   * Zipline's internal encoding of the call. This is intended to help with both debugging and
   * optimization. The content of this string is not a stable format and should not be operated on
   * programmatically.
   *
   * You can reduce the overhead of Zipline calls by making fewer calls or by and shrinking their
   * encoded size.
   */
  val encodedCall: String,

  serviceNames: List<String>,
) {
  /**
   * Names of [ZiplineService] instances passed in the parameters of this call. These are opaque
   * generated IDs that can be correlated to [EventListener.serviceLeaked] if the service is not
   * closed properly.
   */
  val serviceNames: List<String> = serviceNames.toList() // Defensive copy.

  override fun toString() = "Call(receiver=$service, function=${function.signature}, args=$args)"
}
