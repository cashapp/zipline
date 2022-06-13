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
 * Listener for metrics and debugging.
 *
 * All event methods must execute fast, without external locking, cannot throw exceptions, attempt
 * to mutate the event parameters, or be re-entrant back into the client. Any IO - writing to files
 * or network should be done asynchronously.
 */
abstract class EventListener {
  /** Invoked when something calls [Zipline.bind], or a service is sent via an API. */
  open fun bindService(name: String, service: ZiplineService) {
  }

  /** Invoked when something calls [Zipline.take], or a service is received via an API. */
  open fun takeService(name: String, service: ZiplineService) {
  }

  /**
   * Invoked when a service function is called. This may be invoked for either suspending or
   * non-suspending functions.
   *
   * @return any object. This value will be passed back to [callEnd] when the call is completed. The
   *     base function always returns null.
   */
  open fun callStart(call: Call): Any? {
    return null
  }

  /**
   * Invoked when a service function call completes.
   *
   * @param callStartResult the value returned by [callStart] for the start of this call. This is
   *     null unless [callStart] is overridden to return something else.
   */
  open fun callEnd(call: Call, result: CallResult, callStartResult: Any?) {
  }

  /** Invoked when a service is garbage collected without being closed. */
  open fun serviceLeaked(name: String) {
  }

  /** Invoked when an application load starts */
  open fun applicationLoadStart(
    applicationName: String,
    manifestUrl: String?,
  ) {
  }

  /** Invoked when an application load succeeds */
  open fun applicationLoadEnd(
    applicationName: String,
    manifestUrl: String?,
  ) {
  }

  /** Invoked when an application load fails */
  open fun applicationLoadFailed(
    applicationName: String,
    manifestUrl: String?,
    exception: Exception,
  ) {
  }

  /** Invoked when a network download starts */
  open fun downloadStart(
    applicationName: String,
    url: String,
  ) {
  }

  /** Invoked when a network download succeeds */
  open fun downloadEnd(
    applicationName: String,
    url: String,
  ) {
  }

  /** Invoked when a network download fails */
  open fun downloadFailed(
    applicationName: String,
    url: String,
    exception: Exception,
  ) {
  }

  /**
   * Invoked when the manifest couldn't be decoded as JSON. For example, this might occur if there's
   * a captive portal on the network.
   */
  open fun manifestParseFailed(
    applicationName: String,
    url: String?,
    exception: Exception,
  ) {
  }

  companion object {
    val NONE: EventListener = object : EventListener() {
    }
  }
}
