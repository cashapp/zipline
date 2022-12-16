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
 * An opaque set of [ZiplineServices][ZiplineService] that can be closed as a unit. Use this to
 * avoid tracking individual services.
 *
 *
 * # Using ZiplineScope With Take
 *
 * To use this, pass an instance to [Zipline.take], then the returned service and all services
 * it produces can be closed with [ZiplineService.close].
 *
 * ```kotlin
 * val scope = ZiplineScope()
 * val quoteService = zipline.take<QuoteService>("quoteService", scope)
 *
 * val appl: LiveChart = quoteService.chart("APPL")
 * val nke: LiveChart = quoteService.chart("NKE")
 * val sq: LiveChart = quoteService.chart("SQ")
 *
 * scope.close() // closes quoteService, appl, nke, and sq.
 * ```
 *
 * Note that returned services should be closed early if they are no longer needed.
 *
 *
 * # Using ZiplineScope With Bind
 *
 * When defining a service for others to call, implement from [ZiplineScoped] to set the scope that
 * passed-in services will be added to.
 *
 * ```kotlin
 * class RealPriceWatcherService : PriceWatcherService, ZiplineScoped {
 *   override val scope = ZiplineScope()
 *
 *   fun subscribe(listener: PriceListener) {
 *     ...
 *   }
 *
 *   override fun close() {
 *     scope.close() // closes all listeners ever passed to subscribe.
 *   }
 * }
 * ```
 *
 * When a service does not implement [ZiplineScoped], the services passed in to it must be closed
 * individually.
 */
class ZiplineScope {
  internal var closed = false
    private set

  private val services = mutableSetOf<ZiplineService>()

  internal fun add(result: ZiplineService) {
    check(!closed)
    services += result
  }

  internal fun remove(result: ZiplineService) {
    services -= result
  }

  fun close() {
    if (closed) return
    closed = true

    val servicesCopy = services.toTypedArray() // Because ZiplineService.close() mutates the set.
    services.clear()
    for (service in servicesCopy) {
      service.close()
    }
  }
}
