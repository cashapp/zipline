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

import app.cash.zipline.ZiplineService

/**
 * A bridged interface to cancel suspending calls. This is used internally by Zipline to send
 * a cancellation signal from the calling endpoint to the receiving endpoint.
 *
 * It is not necessary for the calling endpoint to [ZiplineService.close] this; that's handled
 * automatically by the receiving service.
 */
@PublishedApi
internal interface CancelCallback : ZiplineService {
  fun cancel()
}
