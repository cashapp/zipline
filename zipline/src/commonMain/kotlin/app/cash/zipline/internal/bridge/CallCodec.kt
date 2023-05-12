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
package app.cash.zipline.internal.bridge

import app.cash.zipline.Call
import app.cash.zipline.CallResult
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.decodeFromStringFast
import app.cash.zipline.internal.encodeToStringFast
import kotlinx.serialization.KSerializer

/**
 * Manages encoding an [InternalCall] to JSON and back.
 *
 * Zipline's support for both pass-by-value and pass-by-reference is supported by this codec. It
 * keeps track of services passed by reference in both [Call] and [CallResult] instances. Service
 * names are collected as a side effect of serialization by [PassByReferenceSerializer].
 */
internal class CallCodec(
  private val endpoint: Endpoint,
) {
  private val callSerializer = RealCallSerializer(endpoint)

  /** This list collects service names as they're decoded, including members of other types. */
  val decodedServiceNames = mutableListOf<String>()

  /** This list collects service names as they're encoded, including members of other types. */
  val encodedServiceNames = mutableListOf<String>()

  /**
   * The most-recently received inbound call. Due to reentrant calls it's possible that this isn't
   * the currently-executing call.
   */
  var lastInboundCall: Call? = null

  var nextOutboundCallCallback: ((Call) -> Unit)? = null

  internal fun encodeCall(
    internalCall: InternalCall,
    service: ZiplineService,
  ): Call {
    encodedServiceNames.clear()
    val encodedCall = endpoint.json.encodeToStringFast(callSerializer, internalCall)
    val result = Call(
      internalCall.serviceName,
      service,
      internalCall.function,
      internalCall.args,
      encodedCall,
      encodedServiceNames,
    )

    val callback = nextOutboundCallCallback
    if (callback != null) {
      callback(result)
      nextOutboundCallCallback = null
    }

    return result
  }

  internal fun decodeCall(
    callJson: String,
  ): InternalCall {
    decodedServiceNames.clear()
    val internalCall = endpoint.json.decodeFromStringFast(callSerializer, callJson)
    val inboundService = internalCall.inboundService
      ?: error("no handler for ${internalCall.serviceName}")
    lastInboundCall = Call(
      internalCall.serviceName,
      inboundService.service,
      internalCall.function,
      internalCall.args,
      callJson,
      decodedServiceNames,
    )
    return internalCall
  }

  internal fun encodeResult(
    function: ReturningZiplineFunction<*>,
    result: Result<Any?>,
  ): CallResult {
    encodedServiceNames.clear()
    @Suppress("UNCHECKED_CAST") // We delegate to the right serializer to encode.
    val resultJson = endpoint.json.encodeToStringFast(
      function.resultSerializer as KSerializer<Any?>,
      ResultOrCallback(result),
    )
    return CallResult(result, resultJson, encodedServiceNames)
  }

  internal fun encodeResultOrCallback(
    function: SuspendingZiplineFunction<*>,
    result: ResultOrCallback<*>,
  ): EncodedResultOrCallback {
    encodedServiceNames.clear()
    @Suppress("UNCHECKED_CAST") // We delegate to the right serializer to encode.
    val resultOrCallbackJson = endpoint.json.encodeToStringFast(
      function.resultOrCallbackSerializer as KSerializer<Any?>,
      result,
    )
    return EncodedResultOrCallback(result, resultOrCallbackJson, encodedServiceNames)
  }

  internal fun decodeResult(
    function: ReturningZiplineFunction<*>,
    resultJson: String,
  ): CallResult {
    decodedServiceNames.clear()
    val result = endpoint.json.decodeFromStringFast(
      function.resultSerializer,
      resultJson,
    )
    return CallResult(result.result!!, resultJson, decodedServiceNames)
  }

  internal fun decodeResultOrCallback(
    function: SuspendingZiplineFunction<*>,
    resultOrCallbackJson: String,
  ): EncodedResultOrCallback {
    decodedServiceNames.clear()
    val result = endpoint.json.decodeFromStringFast(
      function.resultOrCallbackSerializer,
      resultOrCallbackJson,
    )
    return EncodedResultOrCallback(result, resultOrCallbackJson, decodedServiceNames)
  }
}
