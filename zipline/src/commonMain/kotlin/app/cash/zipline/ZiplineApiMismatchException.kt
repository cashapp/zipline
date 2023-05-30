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

class ZiplineApiMismatchException(override val message: String) : Exception(message) {
  internal companion object {
    /**
     * This magic value is detected by the bridge and replaced with an actionable list of available
     * functions.
     */
    internal const val UNKNOWN_FUNCTION = "<unknown function>"

    /**
     * This magic value is detected by the bridge and replaced with an actionable list of available
     * services.
     */
    internal const val UNKNOWN_SERVICE = "<unknown service>"
  }
}
