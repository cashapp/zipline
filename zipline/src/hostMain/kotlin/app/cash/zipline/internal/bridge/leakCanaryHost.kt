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
package app.cash.zipline.internal.bridge

/**
 * Undo the leak tracking created by prior calls to [trackLeaks] with [endpoint]. This is called
 * when the endpoint itself is closed, which means its no longer at risk of holding a bound service
 * longer than required.
 */
internal expect fun stopTrackingLeaks(
  endpoint: Endpoint,
)
