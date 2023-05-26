/*
 * Copyright (C) 2021 Square, Inc.
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
package app.cash.zipline.internal

import android.util.Log

internal actual fun log(level: String, message: String, throwable: Throwable?) {
  when (level) {
    "warn" -> Log.w("Zipline", message, throwable)
    "error" -> Log.e("Zipline", message, throwable)
    else -> Log.i("Zipline", message, throwable)
  }
}
