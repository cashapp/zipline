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

import app.cash.zipline.internal.bridge.OutboundService

/**
 * Implement this interface on inbound services to specify which scope service arguments are added
 * to. See [ZiplineScope] for details.
 *
 * Do not extend this in your [ZiplineService] interfaces; it should only be implemented by concrete
 * implementation classes.
 */
interface ZiplineScoped {
  /**
   * Returns the scope that will be used for services passed as parameters to this service. Note
   * that the declaring service is not added to this scope.
   */
  val scope: ZiplineScope
}

/**
 * Returns a service that shares the same target object as this, but that uses [scope] to manage
 * closing returned services.
 *
 * Use this function to apply different scopes to objects with different lifetimes. For example,
 * this uses a narrower scope to close two services once they're unneeded.
 *
 * ```kotlin
 * val scopeA = ZiplineScope()
 * val quoteService = zipline.take<QuoteService>("quoteService", scopeA)
 *
 * val appl: LiveChart = quoteService.chart("APPL")
 *
 * val scopeB = ZiplineScope()
 * val quoteServiceWithScopeB = quoteService.withScope(scopeB)
 * val nke: LiveChart = quoteServiceWithScopeB.chart("NKE")
 * val sq: LiveChart = quoteServiceWithScopeB.chart("SQ")
 * scopeB.close() // closes nke and sq.
 *
 * scopeA.close() // closes quoteService and appl.
 * ```
 *
 * This may also be used to extend the lifetime of an object beyond the lifetime of the object that
 * produced it.
 *
 * ```kotlin
 * val scopeA = ZiplineScope()
 * val quoteService = zipline.take<QuoteService>("quoteService", scopeA)
 *
 * val scopeB = ZiplineScope()
 * val quoteServiceWithScopeB = quoteService.withScope(scopeB)
 * val nke: LiveChart = quoteServiceWithScopeB.chart("NKE")
 *
 * scopeA.close() // closes quoteService.
 *
 * nke.setPeriod(...)
 * nke.setPrecision(...)
 * scopeB.close() // closes nke.
 * ```
 *
 * Note that closing the returned service will close the receiver: the two handles target the same object.
 * Closing either will not close [scope].
 */
fun <T : ZiplineService> T.withScope(scope: ZiplineScope): T {
  require(this is OutboundService) { "cannot scope $this; it isn't an outbound service" }
  return callHandler.withScope(scope).outboundService()
}
