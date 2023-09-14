/*
 * Copyright (C) 2023 Cash App
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
package app.cash.zipline.testing

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Returns a CoroutineDispatcher that's confined to a single thread, appropriate for executing
 * QuickJS. The dispatcher starts its thread lazily on first use.
 *
 * On Apple platforms we need to explicitly set the stack size for background threads; otherwise we
 * get the default of 512 KiB which isn't sufficient for our QuickJS programs.
 */
@ExperimentalCoroutinesApi
expect fun singleThreadCoroutineDispatcher(
  name: String,
  stackSize: Int = 1024 * 1024,
): CloseableCoroutineDispatcher
